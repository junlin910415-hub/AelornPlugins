package tw.linsy.aelornstore.model;

import org.jetbrains.annotations.Nullable;

/**
 * One line of the tamper-evident trail.
 *
 * {@code target} is normally a player UUID so a dispute can be answered with a
 * single indexed lookup; {@code actor} records who caused it, which for anything
 * money-related is the difference between a refund and an accusation.
 */
public record AuditEntry(
    long id,
    String actor,
    String action,
    @Nullable String target,
    @Nullable String detail,
    long createdAt
) { }
