package com.campusguard.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param perUserPerHour newly queued model batches per account. Cached
 *     translations and polls for already queued text do not consume this budget.
 * @param maxCharsPerCall how much source text one model call carries. Larger
 *     batches are cheaper per item but one malformed answer then loses more.
 * @param maxCallsPerRequest batches one HTTP request may queue. HTTP never waits
 *     for these calls; remaining text is reported pending and fetched later.
 */
@ConfigurationProperties(prefix = "campusguard.translation")
public record TranslationProperties(
        @DefaultValue("60") int perUserPerHour,
        @DefaultValue("6000") int maxCharsPerCall,
        @DefaultValue("3") int maxCallsPerRequest) {
}
