package com.campusguard.media;

import com.campusguard.common.NotFoundException;
import com.campusguard.moderation.ContentLocator;
import com.campusguard.moderation.ModerationCase;
import com.campusguard.moderation.ModerationCaseRepository;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Evidence access is restricted by SecurityConfig's administrator rule and case scope. */
@RestController
@RequestMapping("/api/admin/moderation-cases/{caseId}/media")
public class AdminMediaController {
    private final MediaService service;
    private final ModerationCaseRepository cases;
    private final ContentLocator content;

    public AdminMediaController(
            MediaService service, ModerationCaseRepository cases, ContentLocator content) {
        this.service = service;
        this.cases = cases;
        this.content = content;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> read(@PathVariable UUID caseId, @PathVariable UUID id) {
        ModerationCase moderationCase = cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("No moderation case with id " + caseId));
        boolean reported = moderationCase.reportedContent()
                .map(item -> id.equals(item.mediaId())).orElse(false);
        boolean current = content.findIncludingRemoved(
                moderationCase.getTargetType(), moderationCase.getTargetId())
                .map(item -> id.equals(item.mediaId())).orElse(false);
        if (!reported && !current) {
            throw new NotFoundException("Media " + id + " does not belong to case " + caseId);
        }
        MediaService.StoredMedia stored = service.readEvidence(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.metadata().getContentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(stored.bytes());
    }
}
