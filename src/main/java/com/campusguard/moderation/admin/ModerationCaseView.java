package com.campusguard.moderation.admin;

import com.campusguard.common.TargetType;
import com.campusguard.moderation.CaseStatus;
import com.campusguard.moderation.FinalAction;
import com.campusguard.moderation.ModerationCase;
import com.campusguard.moderation.ModerationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A case as the moderation console shows it.
 *
 * <p>{@code decision}, {@code confidence} and {@code rationale} are presented as
 * a recommendation and never as an outcome: the reviewer's job is to agree or
 * not, and a field named for what "will happen" would quietly turn the review
 * into a rubber stamp.
 */
public record ModerationCaseView(
        UUID id,
        TargetType targetType,
        UUID targetId,
        CaseStatus status,
        int reportCount,
        String engine,
        ModerationDecision recommendedDecision,
        BigDecimal confidence,
        String rationale,
        List<String> ruleCodes,
        Instant analysedAt,
        UUID decidedBy,
        Instant decidedAt,
        FinalAction finalAction,
        UUID assignedTo,
        Instant assignedAt,
        Instant reviewDueAt,
        Instant createdAt,
        String contentTitle,
        String contentPreview,
        boolean hasAttachment) {

    public static ModerationCaseView of(ModerationCase source) {
        return of(source, null);
    }

    public static ModerationCaseView of(ModerationCase source, com.campusguard.moderation.ContentLocator.ContentPreview fallback) {
        var reported = source.reportedContent();
        String title = reported.map(item -> item.title() == null ? "" : item.title())
                .orElse(fallback == null ? "" : fallback.title());
        String body = reported.map(item -> item.body() == null ? "" : item.body())
                .orElse(fallback == null ? "" : fallback.body());
        boolean attachment = reported.map(item -> item.mediaId() != null)
                .orElse(fallback != null && fallback.hasAttachment());
        return new ModerationCaseView(
                source.getId(),
                source.getTargetType(),
                source.getTargetId(),
                source.getStatus(),
                source.getReportCount(),
                source.getEngine(),
                source.getDecision(),
                source.getConfidence(),
                source.getRationale(),
                source.getRuleCodes(),
                source.getAnalysedAt(),
                source.getDecidedBy() == null ? null : source.getDecidedBy().getId(),
                source.getDecidedAt(),
                source.getFinalAction(),
                source.getAssignedTo() == null ? null : source.getAssignedTo().getId(),
                source.getAssignedAt(),
                source.getReviewDueAt(),
                source.getCreatedAt(), excerpt(title, 120), excerpt(body, 180), attachment);
    }

    private static String excerpt(String value, int limit) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").strip();
        int count = compact.codePointCount(0, compact.length());
        return count <= limit ? compact : compact.substring(0, compact.offsetByCodePoints(0, limit)) + "…";
    }
}
