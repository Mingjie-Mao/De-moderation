package com.campusguard.translation;

import com.campusguard.auth.RequestRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Serves machine translations of live posts and comments, translating each text
 * once per language and sharing the result with every later reader.
 *
 * <p>Cache misses are coalesced in a bounded background executor. HTTP returns
 * immediately with cached results and pending=true, never waits for a model,
 * and holds neither a transaction nor a connection while the model runs.
 *
 * <p>Moderation never reads this table. Reports, evidence and decisions are about
 * what the author wrote, and a translation is only a reading aid.
 */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final JdbcClient db;
    private final ObjectProvider<Translator> translator;
    private final RequestRateLimiter rateLimiter;
    private final TranslationProperties properties;
    private final ExecutorService executor;
    private final ConcurrentMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public TranslationService(
            JdbcClient db,
            ObjectProvider<Translator> translator,
            RequestRateLimiter rateLimiter,
            TranslationProperties properties,
            @Qualifier("translationExecutor") ExecutorService executor) {
        this.db = db;
        this.translator = translator;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.executor = executor;
    }

    public record Item(UUID id, String title, String body) {
    }

    /**
     * @param pending true when some texts could not be translated yet, because
     *     no model is configured, it failed, or this request reached its budget
     */
    public record Result(String language, List<Item> posts, List<Item> comments, boolean pending) {
    }

    public record EvidenceItem(UUID id, String title, String body, String sourceTitle, String sourcePreview) {}
    public record EvidenceResult(String language, List<EvidenceItem> cases, boolean pending) {}
    private record Work(Map<String, Item> done, boolean pending) {}

    private record Source(String type, UUID id, String title, String body, String hash) {
        String key() {
            return type.charAt(0) + ":" + id;
        }
    }

    public Result translate(UUID viewer, String language, List<UUID> postIds, List<UUID> commentIds) {
        List<Source> sources = new ArrayList<>();
        sources.addAll(posts(postIds));
        sources.addAll(comments(commentIds));
        sources.removeIf(source -> alreadyIn(language, source));
        Work work = resolve(viewer, language, sources);
        Map<String, Item> done = work.done();
        List<Item> posts = new ArrayList<>();
        List<Item> comments = new ArrayList<>();
        for (Source source : sources) {
            Item item = done.get(source.key());
            if (item != null) (source.type().equals("POST") ? posts : comments).add(item);
        }
        return new Result(language, posts, comments, work.pending());
    }

    /** Translate exactly what was reported, even after edits, hiding or deletion. */
    public EvidenceResult translateEvidence(UUID viewer, String language, List<UUID> ids) {
        List<Source> sources = evidence(ids);
        Work work = resolve(viewer, language, sources.stream().filter(s -> !alreadyIn(language, s)).toList());
        List<EvidenceItem> items = new ArrayList<>();
        for (Source source : sources) {
            Item item = alreadyIn(language, source) ? new Item(source.id(), source.title(), source.body())
                    : work.done().get(source.key());
            if (item != null) items.add(new EvidenceItem(item.id(), item.title(), item.body(),
                    excerpt(source.title(), 120), excerpt(source.body(), 180)));
        }
        return new EvidenceResult(language, items, work.pending());
    }

    private Work resolve(UUID viewer, String language, List<Source> sources) {
        Map<String, Item> done = cached(language, sources);
        List<Source> missing = sources.stream().filter(source -> !done.containsKey(source.key())).toList();
        boolean pending = false;

        if (!missing.isEmpty()) {
            Translator model = translator.getIfAvailable();
            if (model == null) {
                pending = true;
            } else {
                queueMissing(model, viewer, language, missing);
                pending = true;
            }
        }

        return new Work(done, pending);
    }

    private List<Source> evidence(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return db.sql("""
                SELECT mc.id,
                  CASE WHEN mc.target_type='POST' THEN
                    CASE WHEN mc.reported_at IS NOT NULL THEN mc.reported_title ELSE p.title END END AS title,
                  coalesce(CASE WHEN mc.reported_at IS NOT NULL THEN mc.reported_body
                    WHEN mc.target_type='POST' THEN p.body ELSE c.body END, '') AS body
                FROM moderation_cases mc
                LEFT JOIN posts p ON mc.target_type='POST' AND p.id=mc.target_id
                LEFT JOIN comments c ON mc.target_type='COMMENT' AND c.id=mc.target_id
                WHERE mc.id IN (:ids)
                """).param("ids", ids)
                .query((rs, n) -> source("EVIDENCE", rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body")))
                .list();
    }

    private static String excerpt(String value, int limit) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").strip();
        return compact.codePointCount(0, compact.length()) <= limit ? compact
                : compact.substring(0, compact.offsetByCodePoints(0, limit)) + "…";
    }

    private void queueMissing(Translator model, UUID viewer, String language, List<Source> missing) {
        List<List<Source>> batches = batches(missing);
        for (List<Source> batch : batches.subList(0, Math.min(batches.size(), properties.maxCallsPerRequest()))) {
            List<Source> fresh = batch.stream()
                    .filter(source -> inFlight.putIfAbsent(workKey(language, source), true) == null).toList();
            if (fresh.isEmpty()) continue; // Polling or another reader is not another paid call.
            try {
                rateLimiter.consume("translate", viewer.toString(), properties.perUserPerHour(),
                        Duration.ofHours(1), "Too many translation requests. Original text is shown for now.");
                executor.execute(() -> {
                    try { translateMissing(model, language, fresh, new LinkedHashMap<>()); }
                    catch (RuntimeException error) {
                        log.warn("Translation worker failed language={} category={}", language, error.getClass().getSimpleName());
                    } finally { fresh.forEach(source -> inFlight.remove(workKey(language, source))); }
                });
            } catch (RejectedExecutionException full) {
                fresh.forEach(source -> inFlight.remove(workKey(language, source)));
                // The original text remains usable; a later poll can retry.
                return;
            } catch (RuntimeException error) {
                fresh.forEach(source -> inFlight.remove(workKey(language, source)));
                throw error;
            }
        }
    }

    private static String workKey(String language, Source source) {
        return language + ":" + source.key() + ":" + source.hash();
    }

    /** @return true when every missing text was translated */
    private boolean translateMissing(Translator model, String language, List<Source> missing, Map<String, Item> done) {
        List<List<Source>> batches = batches(missing);
        int calls = Math.min(batches.size(), properties.maxCallsPerRequest());
        boolean complete = calls == batches.size();

        for (List<Source> batch : batches.subList(0, calls)) {
            Map<String, String> texts = new LinkedHashMap<>();
            for (Source source : batch) {
                if (source.title() != null) texts.put(source.key() + ":t", source.title());
                if (!source.body().isBlank()) texts.put(source.key() + ":b", source.body());
            }
            Map<String, String> answer;
            try {
                answer = model.translate(texts, language);
            } catch (RuntimeException ex) {
                // The model being down is not the reader's problem: they see the
                // original, and the next read asks again.
                log.warn("Translation into {} failed category={}", language, ex.getClass().getSimpleName());
                return false;
            }
            for (Source source : batch) {
                // A post may be all title; its empty body needs no translating.
                String body = source.body().isBlank() ? "" : answer.get(source.key() + ":b");
                String title = source.title() == null ? null : answer.get(source.key() + ":t");
                if (body == null || (source.title() != null && title == null)) {
                    complete = false;
                    continue;
                }
                store(source, language, title, body, model.modelName());
                done.put(source.key(), new Item(source.id(), title, body));
            }
        }
        return complete;
    }

    private List<List<Source>> batches(List<Source> sources) {
        List<List<Source>> batches = new ArrayList<>();
        List<Source> current = new ArrayList<>();
        int chars = 0;
        for (Source source : sources) {
            int size = source.body().length() + (source.title() == null ? 0 : source.title().length());
            if (!current.isEmpty() && chars + size > properties.maxCharsPerCall()) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(source);
            chars += size;
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    private List<Source> posts(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return db.sql("SELECT id, title, coalesce(body, '') AS body FROM posts WHERE id IN (:ids) AND deleted_at IS NULL")
                .param("ids", ids)
                .query((rs, n) -> source("POST", rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body")))
                .list();
    }

    private List<Source> comments(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return db.sql("""
                        SELECT c.id, coalesce(c.body, '') AS body FROM comments c JOIN posts p ON p.id = c.post_id
                        WHERE c.id IN (:ids) AND c.deleted_at IS NULL AND p.deleted_at IS NULL
                        """)
                .param("ids", ids)
                .query((rs, n) -> source("COMMENT", rs.getObject("id", UUID.class), null, rs.getString("body")))
                .list();
    }

    private Map<String, Item> cached(String language, List<Source> sources) {
        Map<String, Item> found = new LinkedHashMap<>();
        if (sources.isEmpty()) return found;
        Map<String, Source> byKey = new LinkedHashMap<>();
        sources.forEach(source -> byKey.put(source.key(), source));
        db.sql("""
                        SELECT target_type, target_id, source_hash, title, body FROM content_translations
                        WHERE language = :language AND target_id IN (:ids)
                        """)
                .param("language", language)
                .param("ids", sources.stream().map(Source::id).toList())
                .query((rs, n) -> {
                    UUID id = rs.getObject("target_id", UUID.class);
                    Source source = byKey.get(rs.getString("target_type").charAt(0) + ":" + id);
                    // An edit since the translation makes it a translation of
                    // something the author no longer says.
                    if (source != null && source.hash().equals(rs.getString("source_hash"))) {
                        found.put(source.key(), new Item(id, rs.getString("title"), rs.getString("body")));
                    }
                    return null;
                })
                .list();
        return found;
    }

    private void store(Source source, String language, String title, String body, String model) {
        db.sql("""
                        INSERT INTO content_translations (target_type, target_id, language, source_hash, title, body, model)
                        VALUES (:type, :id, :language, :hash, :title, :body, :model)
                        ON CONFLICT (target_type, target_id, language) DO UPDATE SET
                            source_hash = EXCLUDED.source_hash, title = EXCLUDED.title, body = EXCLUDED.body,
                            model = EXCLUDED.model, created_at = now()
                        """)
                .param("type", source.type())
                .param("id", source.id())
                .param("language", language)
                .param("hash", source.hash())
                .param("title", title)
                .param("body", body)
                .param("model", model)
                .update();
    }

    private static Source source(String type, UUID id, String title, String body) {
        return new Source(type, id, title, body, sha256(Optional.ofNullable(title).orElse("") + '\u0000' + body));
    }

    /**
     * Han characters carry several letters' worth of meaning, so a post counts as
     * Chinese once they reach half the Latin letters. Course codes and names
     * inside Chinese text do not then send it to the model.
     */
    private static boolean alreadyIn(String language, Source source) {
        String text = Optional.ofNullable(source.title()).orElse("") + " " + source.body();
        long han = text.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN).count();
        long latin = text.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN).count();
        if (han == 0 && latin == 0) return true;
        return language.equals("zh-CN") ? han * 2 >= latin : han == 0;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every JVM.", ex);
        }
    }
}
