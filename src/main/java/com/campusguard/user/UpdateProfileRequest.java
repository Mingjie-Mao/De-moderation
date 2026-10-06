package com.campusguard.user;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 2000) String bio,
        UUID avatarMediaId,
        Boolean removeAvatar,
        @Min(0) @Max(9) Integer avatarColor,
        @Pattern(regexp = "en|zh-CN") String languageTag,
        @Pattern(regexp = "light|dark") String theme) {
    public UpdateProfileRequest(String displayName, String bio) {
        this(displayName, bio, null, null, null, null, null);
    }
}
