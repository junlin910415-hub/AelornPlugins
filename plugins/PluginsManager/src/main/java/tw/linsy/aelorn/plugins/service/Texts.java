package tw.linsy.aelorn.plugins.service;

import java.util.Collection;
import tw.linsy.aelorn.plugins.platform.MessageCatalog;

/**
 * The two string helpers that are genuinely shared, and nothing else.
 *
 * <p>The previous version had a {@code Texts} class holding colour translation,
 * plain-text stripping, joining, a boolean-to-state formatter and a throwable
 * formatter — five unrelated jobs, three of which are now the renderer's and one
 * of which hard-coded the words "啟用" and "停用" in Java. What is left here is
 * joining (which needs the catalog only for its separator and empty marker) and
 * summarising a throwable for a log line.
 */
public final class Texts {

    private Texts() {
    }

    /**
     * Joins names for display, using the separator and empty marker from
     * messages.yml so both stay translatable.
     */
    public static String join(MessageCatalog messages, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return messages.raw("common.none");
        }
        return String.join(messages.raw("common.separator"), values);
    }

    /**
     * A throwable as one line: type and message, no stack.
     *
     * For audit details and chat replies. The full stack still goes to the server
     * log through the logger, because that is where it is useful and chat is not.
     */
    public static String summarise(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
