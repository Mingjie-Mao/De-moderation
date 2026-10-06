package com.campusguard.community;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.post.*;
import com.campusguard.comment.*;
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
}
