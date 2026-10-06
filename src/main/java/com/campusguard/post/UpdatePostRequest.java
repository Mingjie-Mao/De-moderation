package com.campusguard.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20_000) String body,
        UUID mediaId, @Size(max = 50) String category) {
    public UpdatePostRequest(String title, String body, UUID mediaId) { this(title, body, mediaId, null); }
}
