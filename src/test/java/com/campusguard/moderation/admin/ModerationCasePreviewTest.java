package com.campusguard.moderation.admin;

import com.campusguard.common.TargetType;
import com.campusguard.moderation.ContentLocator;
import com.campusguard.moderation.ModerationCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class ModerationCasePreviewTest {
    @Test void frozenSnapshotWinsOverFallbackAndExcerptDoesNotSplitEmoji() throws Exception {
        var constructor = ModerationCase.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var item = constructor.newInstance();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(item, "targetType", TargetType.POST);
        ReflectionTestUtils.setField(item, "targetId", id);
        item.captureEvidence(new ContentLocator.ModeratedContent(TargetType.POST, id,
                "Original\n title", "😀".repeat(181), UUID.randomUUID(), UUID.randomUUID()));
        var result = ModerationCaseView.of(item, new ContentLocator.ContentPreview("Edited title", "Edited body", false));
        assertThat(result.contentTitle()).isEqualTo("Original title");
        assertThat(result.contentPreview()).isEqualTo("😀".repeat(180) + "…");
        assertThat(result.hasAttachment()).isTrue();
    }
}
