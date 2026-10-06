package com.campusguard.media;

import java.time.Instant;
import java.util.UUID;

public record MediaResponse(UUID id, String url, String contentType, long sizeBytes, Instant createdAt) {
    static MediaResponse of(MediaObject value) {
        return new MediaResponse(value.getId(), MediaUrls.publicUrl(value.getId()), value.getContentType(),
                value.getSizeBytes(), value.getCreatedAt());
    }
}
