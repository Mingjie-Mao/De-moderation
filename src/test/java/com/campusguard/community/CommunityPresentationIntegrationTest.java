package com.campusguard.community;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.post.*;
import com.campusguard.comment.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.campusguard.user.User;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityPresentationIntegrationTest extends AbstractIntegrationTest {
  @Autowired PostService posts;
  @Autowired CommentService comments;
  @Autowired JdbcClient db;

  @Test void metadataSurvivesReloadAndMemberCannotSetPin() throws Exception {
    var author=newUser();
    var result=mockMvc.perform(post("/api/posts").header("Authorization",bearer(author))
      .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("forumKey",uniqueForumKey(),"title","Social title","body","Body","category","Social","pinRank",0))))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.category").value("Social"))
      .andExpect(jsonPath("$.pinRank").doesNotExist()).andReturn();
    UUID id=UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    mockMvc.perform(get("/api/posts/"+id)).andExpect(jsonPath("$.category").value("Social"));
    mockMvc.perform(patch("/api/posts/"+id).header("Authorization",bearer(author)).contentType(MediaType.APPLICATION_JSON)
      .content(json(Map.of("title","Updated","body","Body","category","Career")))).andExpect(jsonPath("$.category").value("Career"));
    mockMvc.perform(patch("/api/posts/"+id).header("Authorization",bearer(author)).contentType(MediaType.APPLICATION_JSON)
      .content(json(Map.of("title","Again","body","Body")))).andExpect(jsonPath("$.category").value("Career"));
  }

  @Test void repeatedInteractionsNotifyOnceAndSelfActionsDoNotNotify() throws Exception {
    var author=newUser();var actor=newUser();
    var p=posts.create(author.getId(),new CreatePostRequest(uniqueForumKey(),"Owner","Body"));
    for(int repeat=0;repeat<2;repeat++) {
      mockMvc.perform(put("/api/community/posts/"+p.id()+"/vote").header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}")).andExpect(status().isOk());
      mockMvc.perform(put("/api/community/posts/"+p.id()+"/bookmark").header("Authorization",bearer(actor))).andExpect(status().isOk());
      mockMvc.perform(put("/api/community/users/"+author.getId()+"/follow").header("Authorization",bearer(actor))).andExpect(status().isOk());
    }
    assertThat(db.sql("SELECT type FROM notifications WHERE user_id=:u").param("u",author.getId()).query(String.class).list()).containsExactlyInAnyOrder("LIKE","BOOKMARK","FOLLOW");
    mockMvc.perform(put("/api/community/posts/"+p.id()+"/vote").header("Authorization",bearer(author)).contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}")).andExpect(status().isOk());
    assertThat(db.sql("SELECT count(*) FROM notifications WHERE user_id=:u").param("u",author.getId()).query(Long.class).single()).isEqualTo(3L);
  }

  @Test void commentsArePagedFromDatabaseAndHiddenThreadsAreExcluded() throws Exception {
    var author=newUser();var commenter=newUser();
    var p=posts.create(author.getId(),new CreatePostRequest(uniqueForumKey(),"Thread","Body",null,"Finals"));
    comments.create(p.id(),commenter.getId(),new CreateCommentRequest(null,"First @"+author.getUsername()));
    comments.create(p.id(),commenter.getId(),new CreateCommentRequest(null,"Second"));
    mockMvc.perform(get("/api/community/users/"+commenter.getId()+"/comments?size=1").header("Authorization",bearer(commenter)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasMore").value(true))
      .andExpect(jsonPath("$.items[0].post.category").value("Finals"));
    assertThat(db.sql("SELECT type FROM notifications WHERE user_id=:u").param("u",author.getId()).query(String.class).list()).contains("COMMENT","MENTION");
    mockMvc.perform(post("/api/community/state").header("Authorization",bearer(commenter)).contentType(MediaType.APPLICATION_JSON)
      .content(json(Map.of("postIds",List.of(p.id()),"commentIds",List.of(),"userIds",List.of()))))
      .andExpect(jsonPath("$.posts[0].comments").value(2));
    posts.delete(author.getId(),p.id());
    mockMvc.perform(get("/api/community/users/"+commenter.getId()+"/comments").header("Authorization",bearer(commenter))).andExpect(jsonPath("$.items").isEmpty());
  }

  @Test void listsReadNewestFirstAndPageByOpaqueCursor() throws Exception {
    var author=newUser();var reader=newUser();
    var older=posts.create(author.getId(),new CreatePostRequest(uniqueForumKey(),"Older","Body"));
    var newer=posts.create(author.getId(),new CreatePostRequest(uniqueForumKey(),"Newer","Body"));
    // Saved in the opposite order to creation: the list follows when it was saved.
    for(var p:List.of(newer,older))
      mockMvc.perform(put("/api/community/posts/"+p.id()+"/bookmark").header("Authorization",bearer(reader))).andExpect(status().isOk());
    var first=page("/api/community/posts?kind=BOOKMARKED&size=1",reader);
    assertThat(first.path("items").get(0).path("id").asText()).isEqualTo(older.id().toString());
    assertThat(first.path("hasMore").asBoolean()).isTrue();
    var second=page("/api/community/posts?kind=BOOKMARKED&size=1&cursor="+first.path("nextCursor").asText(),reader);
    assertThat(second.path("items").get(0).path("id").asText()).isEqualTo(newer.id().toString());
    assertThat(second.path("hasMore").asBoolean()).isFalse();
    assertThat(second.path("nextCursor").isNull()).isTrue();

    comments.create(older.id(),reader.getId(),new CreateCommentRequest(null,"First"));
    comments.create(newer.id(),reader.getId(),new CreateCommentRequest(null,"Second"));
    var latest=page("/api/community/users/"+reader.getId()+"/comments?size=1",reader);
    assertThat(latest.path("items").get(0).path("body").asText()).isEqualTo("Second");
    var earlier=page("/api/community/users/"+reader.getId()+"/comments?size=1&cursor="+latest.path("nextCursor").asText(),reader);
    assertThat(earlier.path("items").get(0).path("body").asText()).isEqualTo("First");
    assertThat(earlier.path("hasMore").asBoolean()).isFalse();

    mockMvc.perform(get("/api/community/posts?kind=BOOKMARKED&cursor="+UUID.randomUUID()).header("Authorization",bearer(reader)))
      .andExpect(status().isNotFound());
  }

  @Test void bannedAccountsLeaveRelationListsAndCounts() throws Exception {
    var author=newUser();var kept=newUser();var banned=newUser();
    for(var follower:List.of(kept,banned))
      mockMvc.perform(put("/api/community/users/"+author.getId()+"/follow").header("Authorization",bearer(follower))).andExpect(status().isOk());
    banned.ban();
    userRepository.saveAndFlush(banned);
    var followers=page("/api/community/users/"+author.getId()+"/followers",kept);
    assertThat(followers.path("items")).hasSize(1);
    assertThat(followers.path("items").get(0).path("id").asText()).isEqualTo(kept.getId().toString());
    assertThat(page("/api/community/users/"+banned.getId()+"/following",kept).path("items")).hasSize(1);
    mockMvc.perform(post("/api/community/state").header("Authorization",bearer(kept)).contentType(MediaType.APPLICATION_JSON)
      .content(json(Map.of("postIds",List.of(),"commentIds",List.of(),"userIds",List.of(author.getId())))))
      .andExpect(jsonPath("$.users[0].followers").value(1));
  }

  private JsonNode page(String url,User viewer) throws Exception {
    var result=mockMvc.perform(get(url).header("Authorization",bearer(viewer))).andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
