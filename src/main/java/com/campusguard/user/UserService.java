package com.campusguard.user;

import com.campusguard.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    private final com.campusguard.media.MediaService media;
    public UserService(UserRepository users, com.campusguard.media.MediaService media) {
        this.media = media;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public MyProfileView me(UUID userId) {
        return MyProfileView.of(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public UserView get(UUID userId) {
        return UserView.of(requireUser(userId));
    }

    @Transactional
    public MyProfileView update(UUID userId, UpdateProfileRequest request) {
        User user = users.findForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("No user with id " + userId));
        if (Boolean.TRUE.equals(request.removeAvatar()) && request.avatarMediaId() != null)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Choose an avatar or remove it, not both.");
        if (request.avatarMediaId() != null) media.requireOwned(request.avatarMediaId(), userId);
        user.updatePreferences(request.avatarMediaId(), request.avatarMediaId() != null || Boolean.TRUE.equals(request.removeAvatar()),
                request.avatarColor(), request.languageTag(), request.theme());
        // PATCH semantics: a missing field is retained; an explicit blank bio
        // clears it. Without this, changing only a display name erased the bio.
        user.updateProfile(
                request.displayName() == null ? user.getDisplayName() : request.displayName(),
                request.bio() == null ? user.getBio() : request.bio());
        return MyProfileView.of(user);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user with id " + userId));
    }
}
