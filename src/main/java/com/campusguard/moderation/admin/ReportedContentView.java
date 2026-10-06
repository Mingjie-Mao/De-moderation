package com.campusguard.moderation.admin;

import com.campusguard.moderation.ContentLocator;
import java.util.UUID;

public record ReportedContentView(String title, String body, UUID authorId, String mediaUrl) {

    public static ReportedContentView of(UUID caseId, ContentLocator.ModeratedContent content) {
        return new ReportedContentView(content.title(), content.body(), content.authorId(),
                content.mediaId() == null ? null
                        : "/api/admin/moderation-cases/" + caseId + "/media/" + content.mediaId());
    }
}
