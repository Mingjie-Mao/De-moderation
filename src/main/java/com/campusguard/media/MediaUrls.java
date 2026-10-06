package com.campusguard.media;

import java.util.UUID;

/** Public URL version separates no-store responses from older immutable caches. */
public final class MediaUrls {
    private MediaUrls() {}

    public static String publicUrl(UUID id) {
        return "/api/media/" + id + "?v=2";
    }
}
