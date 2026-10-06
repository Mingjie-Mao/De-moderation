package com.campusguard.post;

import com.campusguard.common.AuthorView;
import com.campusguard.media.MediaUrls;
import java.time.Instant;
import java.util.UUID;

public record PostResponse(UUID id, String forumKey, String title, String body, AuthorView author,
        String mediaUrl, Instant createdAt, String category, Integer pinRank) {
    public PostResponse(UUID id,String forumKey,String title,String body,AuthorView author,Instant createdAt) {
        this(id,forumKey,title,body,author,null,createdAt,"Study",null);
    }
    public PostResponse(UUID id,String forumKey,String title,String body,AuthorView author,String mediaUrl,Instant createdAt) {
        this(id,forumKey,title,body,author,mediaUrl,createdAt,"Study",null);
    }
    static PostResponse of(Post post) {
        return new PostResponse(post.getId(),post.getForumKey(),post.getTitle(),post.getBody(),AuthorView.of(post.getAuthor()),
            post.getMedia()==null?null:MediaUrls.publicUrl(post.getMedia().getId()),post.getCreatedAt(),post.getCategory(),post.getPinRank());
    }
}
