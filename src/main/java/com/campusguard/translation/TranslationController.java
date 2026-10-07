package com.campusguard.translation;

import com.campusguard.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed-in readers only: a translation the cache does not hold costs a model
 * call, and that is bounded per account.
 */
@RestController
@RequestMapping("/api/translations")
public class TranslationController {

    private final TranslationService service;

    public TranslationController(TranslationService service) {
        this.service = service;
    }

    public record Request(
            @NotNull @Pattern(regexp = "en|zh-CN") String language,
            @NotNull @Size(max = 50) List<@NotNull UUID> postIds,
            @NotNull @Size(max = 100) List<@NotNull UUID> commentIds) {
    }

    @PostMapping
    public TranslationService.Result translate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request request) {
        return service.translate(
                AuthenticatedUser.idOf(jwt),
                request.language(),
                request.postIds().stream().distinct().toList(),
                request.commentIds().stream().distinct().toList());
    }
}
