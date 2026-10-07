package com.campusguard.post;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface PostRepository extends JpaRepository<Post, UUID> {

    interface Preview {
        UUID getId();
        String getTitle();
        String getBody();
        UUID getMediaId();
    }

    /** Legacy moderation cases without a frozen snapshot; includes removed content. */
    @Query("select p.id as id, p.title as title, p.body as body, m.id as mediaId from Post p left join p.media m where p.id in :ids")
    List<Preview> findModerationPreviews(@Param("ids") List<UUID> ids);

    /** One bounded page across all forums, rather than scanning every forum on the client. */
    @Query("""
            select p from Post p join fetch p.author left join fetch p.media
            where p.author.id = :authorId and p.deletedAt is null
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findAuthorFirstPage(@Param("authorId") UUID authorId, Pageable pageable);

    @Query("""
            select p from Post p join fetch p.author left join fetch p.media
            where p.author.id = :authorId and p.deletedAt is null
              and (p.createdAt < :beforeCreatedAt or (p.createdAt = :beforeCreatedAt and p.id < :beforeId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findAuthorAfter(@Param("authorId") UUID authorId,
            @Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeId") UUID beforeId, Pageable pageable);

    /**
     * The feed. Written out rather than derived because the author has to be
     * fetched in the same round trip: rendering a feed of N posts through a lazy
     * author association is the classic N+1, and a derived query name cannot
     * express the fetch.
     *
     * <p>Ordering and the {@code deleted_at IS NULL} predicate match
     * {@code idx_posts_forum_created} so the partial index actually gets used.
     */
    /**
     * The feed, from the top or from where a previous page stopped.
     *
     * <p>Written out rather than derived because the author has to be fetched in
     * the same round trip: rendering a feed of N posts through a lazy author
     * association is the classic N+1, and a derived query name cannot express the
     * fetch.
     *
     * <p>Paged by position rather than by offset. A forum feed has new rows
     * arriving at the top, and with an offset every insertion shifts everything
     * down, so a reader paging through sees some posts twice and never sees
     * others. Seeking past a known position is immune to that, and it also lets
     * the database stop reading as soon as it has enough rows instead of counting
     * past the ones it is skipping.
     *
     * <p>The ordering and the predicate match {@code idx_posts_forum_created}, so
     * the partial index carries the whole query.
     */
    @Query("""
            select p from Post p
            join fetch p.author
            left join fetch p.media
            where p.forumKey = :forumKey and p.deletedAt is null
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFeedFirstPage(@Param("forumKey") String forumKey, Pageable pageable);

    /**
     * The next slice, seeking past the position the previous one ended at.
     *
     * <p>A separate method rather than one query with a nullable cursor. Written
     * that way, PostgreSQL cannot infer the type of the null parameter and refuses
     * the statement outright; written this way, each query also carries a single
     * clean predicate for the planner instead of a disjunction it has to see
     * through.
     *
     * <p>The comparison is on the pair, not the instant. Two posts created in the
     * same microsecond would make a timestamp-only boundary ambiguous, and a
     * reader paging across it would see one of them twice or neither.
     */
    @Query("""
            select p from Post p
            join fetch p.author
            left join fetch p.media
            where p.forumKey = :forumKey
              and p.deletedAt is null
              and (p.createdAt < :beforeCreatedAt
                   or (p.createdAt = :beforeCreatedAt and p.id < :beforeId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFeedAfter(
            @Param("forumKey") String forumKey,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable);

    @Query("""
            select p from Post p
            join fetch p.author
            left join fetch p.media
            where p.id = :id and p.deletedAt is null
            """)
    Optional<Post> findLiveById(@Param("id") UUID id);

    /** Serialises evidence capture with a concurrent author edit or deletion. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findForEvidence(@Param("id") UUID id);

    /** Backs the authoring rate limit. Deleted posts still count: the cost being limited was already paid. */
    long countByAuthorIdAndCreatedAtAfter(UUID authorId, Instant since);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    /**
     * Every post this author has written, including ones already removed.
     *
     * <p>Ids only. The caller is on its way to this author's moderation history,
     * not to their content, and pulling whole rows with their authors and media
     * to read one column from each is the feed's N+1 wearing a different hat.
     *
     * <p>Removed posts are included on purpose: a post that was hidden by a
     * moderator is precisely the kind of history the caller is looking for, and
     * filtering it out would make a repeat offender look clean.
     */
    @Query("select p.id from Post p where p.author.id = :authorId")
    List<UUID> findAllIdsByAuthor(@Param("authorId") UUID authorId);
}
