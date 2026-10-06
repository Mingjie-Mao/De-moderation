package com.campusguard.community;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CommunityNotifications {
  private final JdbcClient db;
  public CommunityNotifications(JdbcClient db) { this.db=db; }

  @Transactional(propagation=Propagation.MANDATORY)
  public void send(UUID actor, UUID recipient, String type, String verb, String refType, UUID refId) {
    if(actor.equals(recipient)) return;
    String name=db.sql("SELECT coalesce(display_name,username) FROM users WHERE id=:id").param("id",actor).query(String.class).single();
    String title=name+" "+verb;
    // Rapid repeat toggles should not fill another account's inbox.
    db.sql("""
INSERT INTO notifications(user_id,type,title,body,reference_type,reference_id)
SELECT :u,:t,:title,:body,:rt,:rid WHERE NOT EXISTS(
 SELECT 1 FROM notifications WHERE user_id=:u AND type=:t AND title=:title
 AND reference_id=:rid AND created_at>now()-interval '5 minutes')
""").param("u",recipient).param("t",type).param("title",title).param("body",title)
      .param("rt",refType).param("rid",refId).update();
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public void content(UUID actor, UUID id, boolean comment, String type, String verb) {
    var target=db.sql(comment
      ? "SELECT c.author_id,p.id FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.id=:id"
      : "SELECT author_id,id FROM posts WHERE id=:id").param("id",id).query().singleRow();
    send(actor,(UUID)target.get("author_id"),type,verb,"POST",(UUID)target.get("id"));
  }
}
