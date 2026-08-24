package tw.linsy.aelorn.rpgcore.runtime.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Thread-safe LIFO ownership bag used to make partial module startup transactional. */
public final class RegistrationScope implements AutoCloseable {
    private final Deque<Entry> registrations = new ArrayDeque<>();
    private boolean closed;
    private long generation = 1L;

    public synchronized <T extends AutoCloseable> T add(T registration) {
        Objects.requireNonNull(registration, "registration");
        return add(registration.getClass().getSimpleName(), registration);
    }

    public synchronized <T extends AutoCloseable> T add(String label, T registration) {
        Objects.requireNonNull(registration, "registration");
        String normalizedLabel = label == null || label.isBlank() ? "registration" : label.strip();
        if (closed) {
            RuntimeException failure = new IllegalStateException("Registration scope is already closed");
            try {
                registration.close();
            } catch (Exception | LinkageError closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        registrations.addLast(new Entry(normalizedLabel, registration));
        return registration;
    }

    public synchronized int size() {
        return registrations.size();
    }

    public synchronized boolean closed() {
        return closed;
    }

    /** Removes a completed one-shot registration without closing it a second time. */
    public synchronized boolean release(AutoCloseable registration) {
        Objects.requireNonNull(registration, "registration");
        return registrations.removeIf(entry -> entry.registration() == registration);
    }

    /** Captures this scope generation; queued work is suppressed after the scope is closed. */
    public synchronized Runnable guard(Runnable task) {
        Objects.requireNonNull(task, "task");
        long lease = generation;
        return () -> {
            synchronized (RegistrationScope.this) {
                if (closed || generation != lease) {
                    return;
                }
            }
            task.run();
        };
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        generation++;
        RuntimeException aggregate = null;
        while (!registrations.isEmpty()) {
            Entry entry = registrations.removeLast();
            try {
                entry.registration().close();
            } catch (Exception | LinkageError failure) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException("Could not close module registration " + entry.label());
                }
                aggregate.addSuppressed(failure);
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    private record Entry(String label, AutoCloseable registration) {
    }
}
