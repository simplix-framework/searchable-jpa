package dev.simplecore.searchable.core.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Resolves the library's messages in the caller's locale.
 *
 * <p>The library ships its own bundle. An application may place a {@link MessageSource} in front
 * of it with {@link #init(MessageSource)}: a key that source defines replaces the library's text,
 * and every other key still resolves from the bundle.
 */
public class MessageUtils {

    private static final MessageSource LIBRARY_MESSAGES = libraryMessages();

    private static volatile MessageSource applicationMessages;

    private MessageUtils() {
        // Private constructor to prevent instantiation
    }

    private static MessageSource libraryMessages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages/message");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    /**
     * Places an application message source in front of the library's bundle.
     *
     * @param source the application's messages, or null to use the library's bundle alone
     */
    public static void init(MessageSource source) {
        applicationMessages = source;
    }

    public static String getMessage(String code) {
        return getMessage(code, null);
    }

    public static String getMessage(String code, Object[] args) {
        MessageSource application = applicationMessages;
        if (application != null) {
            String overridden = application.getMessage(code, args, null, LocaleContextHolder.getLocale());
            // A source set up to answer an unknown key with the key itself has no text for it.
            if (overridden != null && !overridden.equals(code)) {
                return overridden;
            }
        }
        return LIBRARY_MESSAGES.getMessage(code, args, LocaleContextHolder.getLocale());
    }
}
