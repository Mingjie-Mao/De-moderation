package com.campusguard.community;

import com.campusguard.common.NotFoundException;
import com.campusguard.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/community")
@Validated
public class CommunityController {
  private final JdbcClient db;

  private final CommunityNotifications notifications;
  public CommunityController(JdbcClient db, CommunityNotifications notifications) {
    this.db = db; this.notifications=notifications;
  }

  public record State(
      @NotNull @Size(max = 100) List<@NotNull UUID> postIds,
      @NotNull @Size(max = 100) List<@NotNull UUID> commentIds,
      @NotNull @Size(max = 100) List<@NotNull UUID> userIds) {}

  public record Vote(@Min(-1) @Max(1) int value) {}

  @PostMapping("/state")
  public Map<String, Object> state(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody State request) {
    UUID me = AuthenticatedUser.idOf(jwt);
    var posts =
        request.postIds().isEmpty()
            ? List.of()
            : db.sql(
                    """
SELECT p.id, coalesce((SELECT sum(value) FROM post_votes WHERE post_id=p.id),0) AS score,
coalesce((SELECT value FROM post_votes WHERE post_id=p.id AND user_id=:me),0) AS vote,
(SELECT count(*) FROM post_bookmarks WHERE post_id=p.id) AS bookmarks,
(SELECT count(*) FROM comments WHERE post_id=p.id AND deleted_at IS NULL) AS comments,
EXISTS(SELECT 1 FROM post_bookmarks WHERE post_id=p.id AND user_id=:me) AS bookmarked
FROM posts p WHERE p.id IN (:ids) AND p.deleted_at IS NULL
""")
                .param("me", me)
                .param("ids", request.postIds())
                .query()
                .listOfRows();
    var comments =
        request.commentIds().isEmpty()
            ? List.of()
            : db.sql(
                    """
SELECT c.id, coalesce((SELECT sum(value) FROM comment_votes WHERE comment_id=c.id),0) AS score,
coalesce((SELECT value FROM comment_votes WHERE comment_id=c.id AND user_id=:me),0) AS vote
FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.id IN (:ids) AND c.deleted_at IS NULL AND p.deleted_at IS NULL
""")
                .param("me", me)
                .param("ids", request.commentIds())
                .query()
                .listOfRows();
    var users =
        request.userIds().isEmpty()
            ? List.of()
            : db.sql(
                    """
SELECT u.id,(SELECT count(*) FROM user_follows WHERE followed_id=u.id) AS followers,
(SELECT count(*) FROM user_follows WHERE follower_id=u.id) AS following,
EXISTS(SELECT 1 FROM user_follows WHERE followed_id=u.id AND follower_id=:me) AS followed,
(SELECT count(*) FROM post_votes v JOIN posts p ON p.id=v.post_id WHERE p.author_id=u.id AND p.deleted_at IS NULL AND v.value=1) AS likes,
(SELECT count(*) FROM post_bookmarks b JOIN posts p ON p.id=b.post_id WHERE p.author_id=u.id AND p.deleted_at IS NULL) AS bookmarks
FROM users u WHERE u.id IN (:ids)
""")
                .param("me", me)
                .param("ids", request.userIds())
                .query()
                .listOfRows();
    return Map.of("posts", posts, "comments", comments, "users", users);
  }

  private void live(UUID id, boolean comment) {
    String sql =
        comment
            ? "SELECT c.id FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.id=:id AND"
                  + " c.deleted_at IS NULL AND p.deleted_at IS NULL FOR SHARE OF c,p"
            : "SELECT id FROM posts WHERE id=:id AND deleted_at IS NULL FOR SHARE";
    if (db.sql(sql).param("id", id).query(UUID.class).optional().isEmpty())
      throw new NotFoundException("Content is unavailable.");
  }

  private void vote(UUID me, UUID id, int value, boolean comment) {
    live(id, comment);
    String table = comment ? "comment_votes" : "post_votes",
        column = comment ? "comment_id" : "post_id";
    if (value == 0)
      db.sql("DELETE FROM " + table + " WHERE user_id=:me AND " + column + "=:id")
          .param("me", me)
          .param("id", id)
          .update();
    else {
      int changed=db.sql(
              "INSERT INTO "
                  + table
                  + "(user_id,"
                  + column
                  + ",value) VALUES(:me,:id,:value) ON CONFLICT(user_id,"
                  + column
                  + ") DO UPDATE SET value=excluded.value WHERE "+table+".value<>excluded.value")
          .param("me", me)
          .param("id", id)
          .param("value", value)
          .update();
      if(changed>0 && value==1) notifications.content(me,id,comment,"LIKE",comment?"liked your comment":"liked your post");
    }
  }

  @PutMapping("/posts/{id}/vote")
  @Transactional
  public Map<String, Object> postVote(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Vote vote) {
    vote(AuthenticatedUser.idOf(jwt), id, vote.value(), false);
    return state(jwt, new State(List.of(id), List.of(), List.of()));
  }

  @PutMapping("/comments/{id}/vote")
  @Transactional
  public Map<String, Object> commentVote(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Vote vote) {
    vote(AuthenticatedUser.idOf(jwt), id, vote.value(), true);
    return state(jwt, new State(List.of(), List.of(id), List.of()));
  }

  @PutMapping("/posts/{id}/bookmark")
  @Transactional
  public Map<String, Object> bookmark(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    live(id, false);
    int inserted=db.sql("INSERT INTO post_bookmarks(user_id,post_id) VALUES(:me,:id) ON CONFLICT DO NOTHING")
        .param("me", AuthenticatedUser.idOf(jwt))
        .param("id", id)
        .update();
    if(inserted>0) notifications.content(AuthenticatedUser.idOf(jwt),id,false,"BOOKMARK","saved your post");
    return state(jwt, new State(List.of(id), List.of(), List.of()));
  }

  @DeleteMapping("/posts/{id}/bookmark")
  @Transactional
  public Map<String, Object> unbookmark(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    db.sql("DELETE FROM post_bookmarks WHERE user_id=:me AND post_id=:id")
        .param("me", AuthenticatedUser.idOf(jwt))
        .param("id", id)
        .update();
    return state(jwt, new State(List.of(id), List.of(), List.of()));
  }

  @PutMapping("/users/{id}/follow")
  @Transactional
  public Map<String, Object> follow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID me = AuthenticatedUser.idOf(jwt);
    if (me.equals(id))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot follow yourself.");
    if (db.sql("SELECT id FROM users WHERE id=:id AND status='ACTIVE' FOR SHARE")
        .param("id", id)
        .query(UUID.class)
        .optional()
        .isEmpty()) throw new NotFoundException("User is unavailable.");
    int inserted=db.sql(
            "INSERT INTO user_follows(follower_id,followed_id) VALUES(:me,:id) ON CONFLICT DO"
                + " NOTHING")
        .param("me", me)
        .param("id", id)
        .update();
    if(inserted>0) notifications.send(me,id,"FOLLOW","followed you","USER",me);
    return state(jwt, new State(List.of(), List.of(), List.of(me, id)));
  }

  @DeleteMapping("/users/{id}/follow")
  @Transactional
  public Map<String, Object> unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID me = AuthenticatedUser.idOf(jwt);
    db.sql("DELETE FROM user_follows WHERE follower_id=:me AND followed_id=:id")
        .param("me", me)
        .param("id", id)
        .update();
    return state(jwt, new State(List.of(), List.of(), List.of(me, id)));
  }

  public enum CollectionKind {
    LIKED,
    BOOKMARKED
  }

  @GetMapping("/posts")
  public Map<String, Object> collection(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam CollectionKind kind,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
    String table = kind == CollectionKind.LIKED ? "post_votes" : "post_bookmarks";
    String positive = kind == CollectionKind.LIKED ? " AND x.value=1" : "";
    var rows =
        db.sql(
                "SELECT p.id,p.category,p.pin_rank,p.forum_key AS \"forumKey\",p.title,p.body,u.id AS"
                    + " author_id,u.username,u.display_name,u.avatar_media_id,u.avatar_color,p.created_at AS \"createdAt\", CASE"
                    + " WHEN p.media_id IS NULL THEN NULL ELSE '/api/media/'||p.media_id||'?v=2'"
                    + " END AS"
                    + " \"mediaUrl\",json_build_object('id',u.id,'username',u.username,'displayName',u.display_name)"
                    + " AS author FROM posts p JOIN users u ON u.id=p.author_id JOIN "
                    + table
                    + " x ON x.post_id=p.id WHERE x.user_id=:me"
                    + positive
                    + " AND p.deleted_at IS NULL AND (:cursor::uuid IS NULL OR p.id>:cursor::uuid)"
                    + " ORDER BY p.id LIMIT :limit")
            .param("me", AuthenticatedUser.idOf(jwt))
            .param("cursor", cursor)
            .param("limit", size + 1)
            .query(
                (rs, n) -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("id", rs.getObject("id"));
                  m.put("forumKey", rs.getString("forumKey"));
                  m.put("category",rs.getString("category")); m.put("pinRank",rs.getObject("pin_rank"));
                  m.put("title", rs.getString("title"));
                  m.put("body", rs.getString("body"));
                  m.put("createdAt", rs.getTimestamp("createdAt").toInstant());
                  m.put("mediaUrl", rs.getString("mediaUrl"));
                  m.put("author", author(rs.getObject("author_id"), rs.getString("username"), rs.getString("display_name"),
                          rs.getObject("avatar_media_id"), rs.getInt("avatar_color")));
                  return m;
                })
            .list();
    return page(rows, size);
  }

  @GetMapping("/users/{id}/{relation:following|followers}")
  public Map<String, Object> people(
      @PathVariable UUID id,
      @PathVariable String relation,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
    boolean following = relation.equals("following");
    String target = following ? "followed_id" : "follower_id",
        owner = following ? "follower_id" : "followed_id";
    var rows =
        db.sql(
                "SELECT u.id,u.username,coalesce(u.display_name,u.username) AS \"displayName\" FROM"
                    + " user_follows f JOIN users u ON u.id=f."
                    + target
                    + " WHERE f."
                    + owner
                    + "=:id AND (:cursor::uuid IS NULL OR u.id>:cursor::uuid) ORDER BY u.id LIMIT"
                    + " :limit")
            .param("id", id)
            .param("cursor", cursor)
            .param("limit", size + 1)
            .query()
            .listOfRows();
    return page(rows, size);
  }


  @GetMapping("/users/{id}/comments")
  public Map<String,Object> authorComments(@PathVariable UUID id,
      @RequestParam(required=false) UUID cursor,
      @RequestParam(defaultValue="30") @Min(1) @Max(100) int size) {
    var rows=db.sql("""
SELECT c.id,c.body,c.parent_comment_id,c.created_at,c.media_id,
 p.id AS post_id,p.forum_key,p.title,p.body AS post_body,p.created_at AS post_created,
 p.media_id AS post_media,p.category,p.pin_rank,
 u.id AS author_id,u.username,u.display_name,u.avatar_media_id,u.avatar_color,pu.id AS post_author_id,pu.username AS post_username,pu.display_name AS post_display,pu.avatar_media_id AS post_avatar,pu.avatar_color AS post_color
FROM comments c JOIN posts p ON p.id=c.post_id JOIN users u ON u.id=c.author_id JOIN users pu ON pu.id=p.author_id
WHERE c.author_id=:id AND c.deleted_at IS NULL AND p.deleted_at IS NULL
AND (:cursor::uuid IS NULL OR c.id>:cursor::uuid) ORDER BY c.id LIMIT :limit
""").param("id",id).param("cursor",cursor).param("limit",size+1).query((rs,n)->{
      var row=new LinkedHashMap<String,Object>(); row.put("id",rs.getObject("id"));
      row.put("body",rs.getString("body")); row.put("parentCommentId",rs.getObject("parent_comment_id"));
      row.put("createdAt",rs.getTimestamp("created_at").toInstant());
      row.put("author",author(rs.getObject("author_id"),rs.getString("username"),rs.getString("display_name"),rs.getObject("avatar_media_id"),rs.getInt("avatar_color")));
      row.put("mediaUrl",rs.getObject("media_id")==null?null:"/api/media/"+rs.getObject("media_id")+"?v=2");
      var post=new LinkedHashMap<String,Object>(); post.put("id",rs.getObject("post_id")); post.put("forumKey",rs.getString("forum_key"));
      post.put("title",rs.getString("title")); post.put("body",rs.getString("post_body")); post.put("createdAt",rs.getTimestamp("post_created").toInstant());
      post.put("category",rs.getString("category")); post.put("pinRank",rs.getObject("pin_rank"));
      post.put("mediaUrl",rs.getObject("post_media")==null?null:"/api/media/"+rs.getObject("post_media")+"?v=2");
      post.put("author",author(rs.getObject("post_author_id"),rs.getString("post_username"),rs.getString("post_display"),rs.getObject("post_avatar"),rs.getInt("post_color")));
      row.put("post",post); return row;
    }).list(); return page(rows,size);
  }

  private static Map<String, Object> author(Object id, String username, String name, Object avatarId, int color) {
    var view=new LinkedHashMap<String,Object>();
    view.put("id",id);view.put("username",username);view.put("displayName",Objects.requireNonNullElse(name,username));
    view.put("avatarUrl",avatarId==null?null:"/api/media/"+avatarId+"?v=2");view.put("avatarColor",color);
    return view;
  }

  private static Map<String, Object> page(List<? extends Map<String, Object>> rows, int size) {
    boolean more = rows.size() > size;
    var items = rows.subList(0, Math.min(size, rows.size()));
    var result = new LinkedHashMap<String, Object>();
    result.put("items", items);
    result.put("hasMore", more);
    result.put("nextCursor", more ? items.get(items.size() - 1).get("id") : null);
    return result;
  }
}
