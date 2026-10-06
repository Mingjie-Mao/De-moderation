package com.campusguard.moderation;

import java.util.UUID;

/** A hint to inspect the database queue after a report commits, never the queued work itself. */
public record ModerationQueueWakeup(UUID caseId) {}
