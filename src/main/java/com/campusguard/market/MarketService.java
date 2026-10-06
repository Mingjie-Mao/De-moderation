package com.campusguard.market;

import com.campusguard.common.ConflictException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An account's wallet lock covers crediting, positions and the immutable trade ledger. */
@Service
public class MarketService {
  private final JdbcClient db;
  private static final List<String> FORUMS = List.of("anu", "unsw", "usyd", "um");

  public MarketService(JdbcClient db) {
    this.db = db;
  }

  public enum Action {
    BUY,
    SELL,
    OPEN_SHORT,
    CLOSE_SHORT
  }

  public record Trade(
      @NotNull UUID requestId,
      @NotBlank @Pattern(regexp = "anu|unsw|usyd|um") String forumKey,
      @NotNull Action action,
      @Min(1) @Max(10000) int units,
      @Min(1) int expectedPrice) {}

  private Map<String, Object> wallet(UUID user) {
    // The UPSERT holds the same per-wallet row lock through transaction commit.
    // Daily crediting and returning the balance need only one database round trip.
    return db.sql("""
INSERT INTO market_wallets(user_id,reset_date) VALUES(:u,:today)
ON CONFLICT(user_id) DO UPDATE SET
 cash=CASE WHEN market_wallets.reset_date<excluded.reset_date THEN greatest(market_wallets.cash,1000) ELSE market_wallets.cash END,
 credits=market_wallets.credits+CASE WHEN market_wallets.reset_date<excluded.reset_date THEN greatest(0,1000-market_wallets.cash) ELSE 0 END,
 reset_date=greatest(market_wallets.reset_date,excluded.reset_date)
RETURNING *
""")
        .param("u", user).param("today", LocalDate.now(ZoneOffset.UTC)).query().singleRow();
  }

  private List<Map<String, Object>> quotes(boolean includeHistory) {
    Instant bucket = Instant.ofEpochSecond(Instant.now().getEpochSecond() / 300 * 300);
    // Derive and record the quote atomically in one database round trip.
    var counts = db.sql("""
WITH counts AS (SELECT f.forum_key,
 (SELECT count(*) FROM posts p WHERE p.forum_key=f.forum_key AND p.deleted_at IS NULL AND p.created_at>=now()-interval '24 hours') AS posts,
 (SELECT count(*) FROM comments c JOIN posts p ON p.id=c.post_id WHERE p.forum_key=f.forum_key AND p.deleted_at IS NULL AND c.deleted_at IS NULL AND c.created_at>=now()-interval '24 hours') AS replies,
 coalesce((SELECT sum(v.value) FROM post_votes v JOIN posts p ON p.id=v.post_id WHERE p.forum_key=f.forum_key AND p.deleted_at IS NULL AND v.created_at>=now()-interval '24 hours'),0)+
 coalesce((SELECT sum(v.value) FROM comment_votes v JOIN comments c ON c.id=v.comment_id JOIN posts p ON p.id=c.post_id WHERE p.forum_key=f.forum_key AND p.deleted_at IS NULL AND c.deleted_at IS NULL AND v.created_at>=now()-interval '24 hours'),0) AS likes
FROM (VALUES ('anu'),('unsw'),('usyd'),('um')) f(forum_key)),
priced AS (
 SELECT counts.*, greatest(1,least(10000,floor(100+2*posts+1.5*replies+0.8*likes+
     5*greatest(0,(posts+replies/5.0-30)/10.0)+0.5)))::integer AS price FROM counts
), recorded AS (
 INSERT INTO market_candles(forum_key,bucket,open,high,low,close)
 SELECT forum_key,:bucket,price,price,price,price FROM priced ORDER BY forum_key
 ON CONFLICT(forum_key,bucket) DO UPDATE SET
 high=greatest(market_candles.high,excluded.close),low=least(market_candles.low,excluded.close),close=excluded.close
 RETURNING forum_key
)
SELECT priced.* FROM priced JOIN recorded USING(forum_key)
""").param("bucket",java.sql.Timestamp.from(bucket)).query().listOfRows();
    var result = new ArrayList<Map<String, Object>>();
    for (var count : counts) {
      String forum = (String) count.get("forum_key");
      long p = number(count, "posts"), r = number(count, "replies"), v = number(count, "likes");
      int price = ((Number)count.get("price")).intValue();
      var quote = new LinkedHashMap<String,Object>();
      quote.put("forumKey",forum); quote.put("price",price);
      quote.put("posts",p); quote.put("replies",r); quote.put("likes",v);
      result.add(quote);
    }
    // Trading and rankings need current prices, not thousands of candle values.
    if (!includeHistory) return result;
    var history = db.sql("""
SELECT f.forum_key,c.bucket,c.open,c.high,c.low,c.close,c.source,
 (SELECT close FROM market_candles b WHERE b.forum_key=f.forum_key AND b.bucket>=now()-interval '24 hours' ORDER BY bucket LIMIT 1) AS baseline
FROM (VALUES ('anu'),('unsw'),('usyd'),('um')) f(forum_key)
CROSS JOIN LATERAL (SELECT bucket,open,high,low,close,source FROM market_candles WHERE forum_key=f.forum_key ORDER BY bucket DESC LIMIT 288) c
ORDER BY f.forum_key,c.bucket
""").query().listOfRows();
    var previous = history;
    var previousPrices = new HashMap<String,Integer>();
    for(var row:previous) if (row.get("baseline") instanceof Number baseline) previousPrices.put((String)row.get("forum_key"), baseline.intValue());
    for(var quote:result) {
      String forum=(String)quote.get("forumKey");
      var candles=new ArrayList<Map<String,Object>>();
      for(var row:history) if(forum.equals(row.get("forum_key")))
        candles.add(Map.of("at",((java.sql.Timestamp)row.get("bucket")).toInstant(),
            "open",row.get("open"),"high",row.get("high"),"low",row.get("low"),"close",row.get("close"),"source",row.get("source")));
      int price=((Number)quote.get("price")).intValue();
      int first=previousPrices.getOrDefault(forum,price);
      quote.put("dayChange",(price-first)*100d/first);quote.put("candles",candles);
    }
    // Preserve the public quote order used by the Android tabs.
    result.sort(Comparator.comparingInt(q->FORUMS.indexOf(q.get("forumKey"))));
    return result;
  }

  private Map<String, Integer> prices(List<Map<String, Object>> quotes) {
    var prices = new HashMap<String, Integer>();
    for (var q : quotes)
      prices.put((String) q.get("forumKey"), ((Number) q.get("price")).intValue());
    return prices;
  }

  private Map<String, Object> portfolio(
      UUID user, Map<String, Object> wallet, Map<String, Integer> prices) {
    var positions =
        db.sql(
                "SELECT forum_key AS \"forumKey\",side,units,cost FROM market_positions WHERE"
                    + " user_id=:u AND units>0 ORDER BY forum_key,side")
            .param("u", user)
            .query()
            .listOfRows();
    long value = 0, cost = 0;
    for (var position : positions) {
      long units = number(position, "units"), basis = number(position, "cost");
      int price = prices.get((String) position.get("forumKey"));
      long marked =
          position.get("side").equals("LONG")
              ? units * price
              : Math.max(0, 2 * basis - units * price);
      position.put("price", price);
      position.put("value", marked);
      position.put("pnl", marked - basis);
      value += marked;
      cost += basis;
    }
    long cash = number(wallet, "cash"), credits = number(wallet, "credits"), assets = cash + value;
    return Map.of(
        "cash",
        cash,
        "credits",
        credits,
        "marketValue",
        value,
        "totalAssets",
        assets,
        "openPnl",
        value - cost,
        "returnPercent",
        (assets - credits) * 100d / credits,
        "positions",
        positions);
  }

  @Transactional
  public Map<String, Object> snapshot(UUID user) {
    var w = wallet(user);
    var quotes = quotes(true);
    return Map.of(
        "quotes",
        quotes,
        "portfolio",
        portfolio(user, w, prices(quotes)),
        "serverTime",
        Instant.now());
  }

  @Transactional
  public Map<String, Object> trade(UUID user, Trade request) {
    var w = wallet(user);
    var existing =
        db
            .sql("SELECT * FROM market_trades WHERE user_id=:u AND request_id=:r")
            .param("u", user)
            .param("r", request.requestId())
            .query()
            .listOfRows()
            .stream()
            .findFirst();
    if (existing.isPresent()) {
      var old = existing.get();
      if (!old.get("forum_key").equals(request.forumKey())
          || !old.get("action").equals(request.action().name())
          || number(old, "units") != request.units()
          || number(old, "expected_price") != request.expectedPrice())
        throw new ConflictException("Request ID was already used for another trade.");
      return tradeResult(old, true);
    }
    var quotes = quotes(false);
    int price = prices(quotes).get(request.forumKey());
    if (price != request.expectedPrice())
      throw new ConflictException("Quote changed. Refresh the market before trading.");
    boolean longSide = request.action() == Action.BUY || request.action() == Action.SELL;
    String side = longSide ? "LONG" : "SHORT";
    db.sql(
            "INSERT INTO market_positions(user_id,forum_key,side,units,cost) VALUES(:u,:f,:s,0,0)"
                + " ON CONFLICT DO NOTHING")
        .param("u", user)
        .param("f", request.forumKey())
        .param("s", side)
        .update();
    var position =
        db.sql(
                "SELECT units,cost FROM market_positions WHERE user_id=:u AND forum_key=:f AND"
                    + " side=:s")
            .param("u", user)
            .param("f", request.forumKey())
            .param("s", side)
            .query()
            .singleRow();
    long cash = number(w, "cash"),
        units = number(position, "units"),
        cost = number(position, "cost"),
        amount,
        after,
        newUnits,
        newCost;
    boolean open = request.action() == Action.BUY || request.action() == Action.OPEN_SHORT;
    if (open) {
      amount = (long) price * request.units();
      if (amount > cash) throw new ConflictException("Insufficient virtual token balance.");
      after = cash - amount;
      newUnits = units + request.units();
      newCost = cost + amount;
      if (newUnits > 10000) throw new ConflictException("Position limit reached.");
    } else {
      if (request.units() > units) throw new ConflictException("Insufficient position units.");
      long released = request.units() == units ? cost : cost * request.units() / units;
      amount =
          longSide
              ? (long) price * request.units()
              : Math.max(0, 2 * released - (long) price * request.units());
      after = cash + amount;
      newUnits = units - request.units();
      newCost = cost - released;
      if (after > 500000000L) throw new ConflictException("Virtual token balance limit reached.");
    }
    db.sql("UPDATE market_wallets SET cash=:cash WHERE user_id=:u")
        .param("cash", after)
        .param("u", user)
        .update();
    db.sql(
            "UPDATE market_positions SET units=:units,cost=:cost WHERE user_id=:u AND forum_key=:f"
                + " AND side=:s")
        .param("units", newUnits)
        .param("cost", newCost)
        .param("u", user)
        .param("f", request.forumKey())
        .param("s", side)
        .update();
    db.sql(
            "INSERT INTO"
                + " market_trades(id,user_id,request_id,forum_key,action,units,price,expected_price,cash_before,cash_after,amount)"
                + " VALUES(:id,:u,:r,:f,:a,:units,:p,:expected,:before,:after,:amount)")
        .param("id", UUID.randomUUID())
        .param("u", user)
        .param("r", request.requestId())
        .param("f", request.forumKey())
        .param("a", request.action().name())
        .param("units", request.units())
        .param("p", price)
        .param("expected", request.expectedPrice())
        .param("before", cash)
        .param("after", after)
        .param("amount", amount)
        .update();
    return tradeResult(
        db.sql("SELECT * FROM market_trades WHERE user_id=:u AND request_id=:r")
            .param("u", user)
            .param("r", request.requestId())
            .query()
            .singleRow(),
        false);
  }

  private Map<String, Object> tradeResult(Map<String, Object> row, boolean replay) {
    return Map.of(
        "id",
        row.get("id"),
        "units",
        row.get("units"),
        "price",
        row.get("price"),
        "cashBefore",
        row.get("cash_before"),
        "cashAfter",
        row.get("cash_after"),
        "amount",
        row.get("amount"),
        "replayed",
        replay);
  }

  @Transactional
  public Map<String, Object> leaderboard(int page, int size) {
    var prices = prices(quotes(false));
    var rows =
        db.sql(
                """
SELECT u.id,u.username,coalesce(u.display_name,u.username) AS "displayName",w.credits,
w.cash+coalesce((SELECT sum(CASE WHEN p.side='LONG' THEN p.units*q.price ELSE greatest(0,2*p.cost-p.units*q.price) END)
  FROM market_positions p JOIN (VALUES ('anu',:anu),('unsw',:unsw),('usyd',:usyd),('um',:um)) q(forum,price) ON q.forum=p.forum_key WHERE p.user_id=u.id),0) AS "totalAssets",
coalesce((SELECT forum_key FROM market_trades WHERE user_id=u.id ORDER BY created_at DESC,id DESC LIMIT 1),'anu') AS "forumKey",
(SELECT count(DISTINCT (created_at AT TIME ZONE 'UTC')::date) FROM market_trades WHERE user_id=u.id) AS "activeDays"
FROM market_wallets w JOIN users u ON u.id=w.user_id WHERE u.status='ACTIVE'
ORDER BY "totalAssets" DESC,u.id LIMIT :limit OFFSET :offset
""")
            .params(prices)
            .param("limit", size + 1)
            .param("offset", page * size)
            .query()
            .listOfRows();
    boolean more = rows.size() > size;
    var items = rows.subList(0, Math.min(size, rows.size()));
    for (var row : items)
      row.put(
          "returnPercent",
          (number(row, "totalAssets") - number(row, "credits")) * 100d / number(row, "credits"));
    return Map.of("items", items, "hasMore", more, "page", page);
  }

  public Map<String, Object> history(UUID user, UUID cursor, int size) {
    var rows =
        db.sql(
                "SELECT id,request_id AS \"requestId\",forum_key AS"
                    + " \"forumKey\",action,units,price,amount,source,cash_after AS"
                    + " \"cashAfter\",created_at AS \"createdAt\" FROM market_trades WHERE"
                    + " user_id=:u AND (:cursor::uuid IS NULL OR (created_at,id)<(SELECT"
                    + " created_at,id FROM market_trades WHERE id=:cursor::uuid AND user_id=:u))"
                    + " ORDER BY created_at DESC,id DESC LIMIT :limit")
            .param("u", user)
            .param("cursor", cursor)
            .param("limit", size + 1)
            .query()
            .listOfRows();
    boolean more = rows.size() > size;
    var items = rows.subList(0, Math.min(size, rows.size()));
    var result = new LinkedHashMap<String, Object>();
    result.put("items", items);
    result.put("hasMore", more);
    result.put("nextCursor", more ? items.get(items.size() - 1).get("id") : null);
    return result;
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }
}
