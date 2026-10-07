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
import org.springframework.web.bind.annotation.*;

/** The /api/admin prefix enforces the current server administrator role. */
@RestController
@RequestMapping("/api/admin/translations")
public class AdminTranslationController {
    private final TranslationService service;
    public AdminTranslationController(TranslationService service) { this.service = service; }
    public record Request(@NotNull @Pattern(regexp="en|zh-CN") String language,
                          @NotNull @Size(max=30) List<@NotNull UUID> caseIds) {}
    @PostMapping
    public TranslationService.EvidenceResult translate(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody Request request) {
        return service.translateEvidence(AuthenticatedUser.idOf(jwt), request.language(),
                request.caseIds().stream().distinct().toList());
    }
}
