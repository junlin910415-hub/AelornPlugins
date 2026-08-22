package tw.linsy.aelorn.plugins.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What an unload actually managed to release.
 *
 * Unloading is eight independent teardown steps and any of them can fail without
 * making the others pointless, so this accumulates rather than throwing. Steps
 * carry message keys, not sentences, for the same reason {@link Reply} does.
 */
public final class UnloadReport {

    /** A teardown step that ran, with the key describing it and its detail. */
    public record Step(String messageKey, String detail) {
    }

    private final List<Step> done = new ArrayList<>();
    private final List<Step> failed = new ArrayList<>();

    public void succeeded(String messageKey) {
        done.add(new Step(messageKey, ""));
    }

    public void succeeded(String messageKey, String detail) {
        done.add(new Step(messageKey, detail));
    }

    public void failed(String messageKey, String detail) {
        failed.add(new Step(messageKey, detail));
    }

    public List<Step> doneSteps() {
        return List.copyOf(done);
    }

    public List<Step> failedSteps() {
        return List.copyOf(failed);
    }

    /**
     * True when nothing failed. A partial unload is still reported as done — the
     * plugin is gone either way, and the caller needs to know which pieces of it
     * may have been left behind rather than a yes/no.
     */
    public boolean clean() {
        return failed.isEmpty();
    }
}
