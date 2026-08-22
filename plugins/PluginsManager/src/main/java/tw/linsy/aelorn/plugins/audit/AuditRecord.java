package tw.linsy.aelorn.plugins.audit;

import java.time.Instant;

/**
 * One thing that happened, as it will be stored.
 *
 * A record rather than six parameters threaded through the sinks, because batching
 * means a record now outlives the call that created it: the timestamp has to be
 * taken when the event happened, not when the row is finally written.
 *
 * @param time   when the operation happened, not when it was persisted
 * @param actor  a player name, {@code console}, {@code watcher} or {@code system}
 * @param action the operation in kebab-case, matching the command name
 * @param target what it acted on
 * @param status {@code SUCCESS}, {@code FAIL}, {@code WARN}, {@code SKIP}, {@code WAIT}
 *               or {@code NOTICE}
 * @param detail free text, already stripped of markup by the caller
 */
public record AuditRecord(Instant time,
                          String actor,
                          String action,
                          String target,
                          String status,
                          String detail) {

    public AuditRecord {
        actor = actor == null ? "" : actor;
        action = action == null ? "" : action;
        target = target == null ? "" : target;
        status = status == null ? "" : status;
        detail = detail == null ? "" : detail;
    }
}
