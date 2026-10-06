package com.campusguard.community;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.market.MarketService;
import com.campusguard.post.Post;
import com.campusguard.post.PostRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

class CommunityMarketIntegrationTest extends AbstractIntegrationTest {
  @Autowired PostRepository posts;
  @Autowired MarketService market;
  @Autowired JdbcClient db;
  @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

  @Test
  void socialPersistsScopesAndHides() throws Exception {
    var author = newUser();
    var a = newUser();
    var b = newUser();
    var p = posts.saveAndFlush(new Post(uniqueForumKey(), author, "Title", "Body"));
    String vote = "/api/community/posts/" + p.getId() + "/vote",
        bookmark = "/api/community/posts/" + p.getId() + "/bookmark";
    for (int i = 0; i < 2; i++)
      mockMvc
          .perform(
              put(vote)
                  .header("Authorization", bearer(a))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"value\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.posts[0].score").value(1));
    mockMvc.perform(put(bookmark).header("Authorization", bearer(a))).andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/community/posts?kind=BOOKMARKED&size=1").header("Authorization", bearer(a)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].author.id").value(author.getId().toString()));
    mockMvc
        .perform(get("/api/community/posts?kind=BOOKMARKED").header("Authorization", bearer(b)))
        .andExpect(jsonPath("$.items").isEmpty());
    for (int i = 0; i < 2; i++)
      mockMvc
          .perform(
              put("/api/community/users/" + author.getId() + "/follow")
                  .header("Authorization", bearer(a)))
          .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/community/users/" + author.getId() + "/followers")
                .header("Authorization", bearer(b)))
        .andExpect(jsonPath("$.items.length()").value(1));
    mockMvc
        .perform(
            put("/api/community/users/" + a.getId() + "/follow").header("Authorization", bearer(a)))
        .andExpect(status().isBadRequest());
    p.softDelete(java.time.Instant.now());
    posts.saveAndFlush(p);
    mockMvc
        .perform(
            put(vote)
                .header("Authorization", bearer(a))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/community/posts?kind=BOOKMARKED").header("Authorization", bearer(a)))
        .andExpect(jsonPath("$.items").isEmpty());
    mockMvc
        .perform(
            post("/api/community/state")
                .header("Authorization", bearer(a))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "postIds",
                            List.of(p.getId()),
                            "commentIds",
                            List.of(),
                            "userIds",
                            List.of()))))
        .andExpect(jsonPath("$.posts").isEmpty());
  }

  @SuppressWarnings("unchecked")
  private int price(UUID user) {
    return ((List<Map<String, Object>>) market.snapshot(user).get("quotes"))
        .stream()
            .filter(q -> q.get("forumKey").equals("anu"))
            .map(q -> ((Number) q.get("price")).intValue())
            .findFirst()
            .orElseThrow();
  }

  @Test
  void tradeReplayLedgerAndServerValuation() throws Exception {
    var user = newUser();
    int price = price(user.getId());
    var req =
        new MarketService.Trade(
            UUID.randomUUID(), "anu", MarketService.Action.OPEN_SHORT, 1, price);
    var trade = market.trade(user.getId(), req);
    assertThat(market.trade(user.getId(), req).get("replayed")).isEqualTo(true);
    assertThat(((Map<?, ?>) market.snapshot(user.getId()).get("portfolio")).get("totalAssets"))
        .isEqualTo(1000L);
    assertThatThrownBy(
            () ->
                market.trade(
                    user.getId(),
                    new MarketService.Trade(
                        req.requestId(), "anu", MarketService.Action.BUY, 1, price)))
        .isInstanceOf(com.campusguard.common.ConflictException.class);
    market.trade(
        user.getId(),
        new MarketService.Trade(
            UUID.randomUUID(), "anu", MarketService.Action.CLOSE_SHORT, 1, price));
    assertThat(((Map<?, ?>) market.snapshot(user.getId()).get("portfolio")).get("cash"))
        .isEqualTo(1000L);
    assertThat(((List<?>) market.history(user.getId(), null, 1).get("items"))).hasSize(1);
    assertThat(market.history(user.getId(), null, 1).get("hasMore")).isEqualTo(true);
    mockMvc
        .perform(get("/api/market/trades").header("Authorization", bearer(newUser())))
        .andExpect(jsonPath("$.items").isEmpty());
    mockMvc
        .perform(
            post("/api/market/trades")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "requestId",
                            UUID.randomUUID(),
                            "forumKey",
                            "bad",
                            "action",
                            "BUY",
                            "units",
                            0,
                            "expectedPrice",
                            1))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/market/leaderboard?size=1").header("Authorization", bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    assertThat(trade.get("cashAfter")).isEqualTo(1000L - price);
  }

  @Test
  void concurrentReplayAndDailyCreditAreSerialized() throws Exception {
    var user = newUser();
    int price = price(user.getId());
    var request =
        new MarketService.Trade(UUID.randomUUID(), "anu", MarketService.Action.BUY, 1, price);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results =
          executor.invokeAll(
              List.of(
                  () -> market.trade(user.getId(), request),
                  () -> market.trade(user.getId(), request)));
      for (var result : results) result.get();
    }
    assertThat(
            db.sql("SELECT count(*) FROM market_trades WHERE user_id=:u")
                .param("u", user.getId())
                .query(Long.class)
                .single())
        .isEqualTo(1);
    db.sql("UPDATE market_wallets SET reset_date=:yesterday WHERE user_id=:u")
        .param("yesterday", java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1))
        .param("u", user.getId())
        .update();
    market.snapshot(user.getId());
    market.snapshot(user.getId());
    assertThat(
            db.sql("SELECT cash FROM market_wallets WHERE user_id=:u")
                .param("u", user.getId())
                .query(Long.class)
                .single())
        .isEqualTo(1000);
    assertThat(
            db.sql("SELECT credits FROM market_wallets WHERE user_id=:u")
                .param("u", user.getId())
                .query(Long.class)
                .single())
        .isEqualTo(1000L + price);
  }

  @Test
  @SuppressWarnings("unchecked")
  void batchedHistoryRemainsBoundedOrderedAndUsesRealDailyBaseline() {
    var user = newUser();
    var bucket = java.time.Instant.ofEpochSecond(java.time.Instant.now().getEpochSecond()/300*300);
    db.sql("""
INSERT INTO market_candles(forum_key,bucket,open,high,low,close)
SELECT 'anu',CAST(:bucket AS timestamptz)-g*interval '5 minutes',80,80,80,80 FROM generate_series(1,299) g
ON CONFLICT(forum_key,bucket) DO UPDATE SET open=80,high=80,low=80,close=80
""").param("bucket",java.sql.Timestamp.from(bucket)).update();
    var quotes = (List<Map<String,Object>>) market.snapshot(user.getId()).get("quotes");
    assertThat(quotes).extracting(q->q.get("forumKey")).containsExactly("anu","unsw","usyd","um");
    var anu=quotes.getFirst();
    var candles=(List<Map<String,Object>>)anu.get("candles");
    assertThat(candles).hasSize(288);
    assertThat(candles).extracting(c->(java.time.Instant)c.get("at")).isSorted();
    int price=((Number)anu.get("price")).intValue();
    assertThat(candles.getLast().get("close")).isEqualTo(price);
    assertThat(anu.get("dayChange")).isEqualTo((price-80)*100d/80);
    var before=db.sql("SELECT open FROM market_candles WHERE forum_key='anu' AND bucket=:b")
        .param("b",java.sql.Timestamp.from(bucket)).query(Integer.class).single();
    market.leaderboard(0,1);
    assertThat(db.sql("SELECT open FROM market_candles WHERE forum_key='anu' AND bucket=:b")
        .param("b",java.sql.Timestamp.from(bucket)).query(Integer.class).single()).isEqualTo(before);
  }

  @Test
  void competingTradesCannotOverspendAndHiddenCommentsCannotBeVoted() throws Exception {
    var user = newUser();
    int price = price(user.getId());
    int units = 1000 / price;
    assertThat(units).isPositive();
    var first =
        new MarketService.Trade(UUID.randomUUID(), "anu", MarketService.Action.BUY, units, price);
    var second =
        new MarketService.Trade(UUID.randomUUID(), "anu", MarketService.Action.BUY, units, price);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures =
          executor.invokeAll(
              List.of(
                  () -> market.trade(user.getId(), first),
                  () -> market.trade(user.getId(), second)));
      int successes = 0, conflicts = 0;
      for (var future : futures)
        try {
          future.get();
          successes++;
        } catch (ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(com.campusguard.common.ConflictException.class);
          conflicts++;
        }
      assertThat(successes).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
    }
    assertThat(
            db.sql("SELECT cash FROM market_wallets WHERE user_id=:u")
                .param("u", user.getId())
                .query(Long.class)
                .single())
        .isBetween(0L, 999L);
    var author = newUser();
    var post = posts.saveAndFlush(new Post(uniqueForumKey(), author, "Visible", "Body"));
    UUID comment = UUID.randomUUID();
    db.sql("INSERT INTO comments(id,post_id,author_id,body,depth) VALUES(:id,:p,:u,'Reply',0)")
        .param("id", comment)
        .param("p", post.getId())
        .param("u", author.getId())
        .update();
    mockMvc
        .perform(
            put("/api/community/comments/" + comment + "/vote")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments[0].score").value(1));
    post.softDelete(java.time.Instant.now());
    posts.saveAndFlush(post);
    mockMvc
        .perform(
            put("/api/community/comments/" + comment + "/vote")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/market")).andExpect(status().isUnauthorized());
  }

  @Test
  void openTradeDoesNotHoldUpOtherAccountsMarketViews() throws Exception {
    var trader = newUser();
    var viewer = newUser();
    int price = price(trader.getId());
    var traded = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      // Keep the trade's transaction open: whatever it locked stays locked.
      var pending =
          executor.submit(
              () ->
                  transactions.execute(
                      status -> {
                        market.trade(
                            trader.getId(),
                            new MarketService.Trade(
                                UUID.randomUUID(), "anu", MarketService.Action.BUY, 1, price));
                        traded.countDown();
                        try {
                          release.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return null;
                      }));
      assertThat(traded.await(10, TimeUnit.SECONDS)).isTrue();
      try {
        var view = CompletableFuture.supplyAsync(() -> market.snapshot(viewer.getId()));
        assertThat(view.get(5, TimeUnit.SECONDS)).containsKey("quotes");
      } finally {
        release.countDown();
      }
      pending.get();
    }
  }
}
