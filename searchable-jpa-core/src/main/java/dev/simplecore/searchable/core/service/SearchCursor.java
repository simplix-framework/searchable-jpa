package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.exception.SearchableJoinException;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.join.EntityPaths;
import dev.simplecore.searchable.core.utils.SearchableValueParser;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

/**
 * Writes a scroll position as text a client can hand back for the next window, and reads it again.
 *
 * <p>The text names the sort values of the row the next window starts after. It is not signed: a
 * client that changes it moves within the rows its own search already returns, because the values
 * are only compared against the sort of the request they come back with.
 */
public final class SearchCursor {

    private static final char FORWARD = 'F';
    private static final char BACKWARD = 'B';
    private static final char OFFSET = 'O';

    private SearchCursor() {
    }

    /**
     * @param position a keyset or an offset position
     * @return the position as URL-safe text
     * @throws SearchableOperationException for a position of another kind
     */
    public static String encode(ScrollPosition position) {
        StringBuilder text = new StringBuilder();
        if (position instanceof OffsetScrollPosition offset) {
            text.append(OFFSET);
            if (!offset.isInitial()) {
                text.append(offset.getOffset());
            }
        } else if (position instanceof KeysetScrollPosition keyset) {
            text.append(keyset.scrollsForward() ? FORWARD : BACKWARD);
            for (Map.Entry<String, Object> key : keyset.getKeys().entrySet()) {
                text.append('&').append(URLEncoder.encode(key.getKey(), StandardCharsets.UTF_8));
                if (key.getValue() != null) {
                    text.append('=').append(URLEncoder.encode(textOf(key.getValue()), StandardCharsets.UTF_8));
                }
            }
        } else {
            throw new SearchableOperationException(MessageUtils.getMessage("scroll.position.unsupported",
                    new Object[]{position.getClass().getSimpleName()}));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param cursor text written by {@link #encode}, or null or blank for the first window
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity being scrolled, whose attribute types the values are read as
     * @return the position
     * @throws SearchableValidationException when the text is not a cursor of this entity
     */
    public static ScrollPosition decode(String cursor, EntityManager entityManager, Class<?> entityClass) {
        return decode(cursor, path -> {
            try {
                return EntityPaths.javaType(entityManager, entityClass, path);
            } catch (SearchableJoinException e) {
                throw invalid(e);
            }
        });
    }

    /**
     * @param cursor text written by {@link #encode}, or null or blank for the first window
     * @param typeOf the Java type of the value each sort property holds
     * @return the position
     * @throws SearchableValidationException when the text is not a cursor
     */
    public static ScrollPosition decode(String cursor, Function<String, Class<?>> typeOf) {
        if (cursor == null || cursor.isBlank()) {
            return ScrollPosition.keyset();
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            char kind = text.isEmpty() ? 0 : text.charAt(0);
            if (kind == OFFSET) {
                return text.length() == 1 ? ScrollPosition.offset() : ScrollPosition.offset(Long.parseLong(text.substring(1)));
            }
            if (kind != FORWARD && kind != BACKWARD) {
                throw invalid(null);
            }
            Map<String, Object> keys = new LinkedHashMap<>();
            for (String part : text.substring(1).split("&")) {
                if (part.isEmpty()) {
                    continue;
                }
                int separator = part.indexOf('=');
                String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), StandardCharsets.UTF_8);
                keys.put(key, separator < 0 ? null
                        : valueOf(URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8), typeOf.apply(key)));
            }
            return ScrollPosition.of(keys, kind == FORWARD ? ScrollPosition.Direction.FORWARD : ScrollPosition.Direction.BACKWARD);
        } catch (IllegalArgumentException | SearchableParseException e) {
            throw invalid(e);
        }
    }

    /** Text is kept as written; the search value parser would normalise it and read "null" as no value. */
    private static Object valueOf(String text, Class<?> type) {
        return type == String.class ? text : SearchableValueParser.parseValue(text, type);
    }

    private static String textOf(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        return value.toString();
    }

    private static SearchableValidationException invalid(Exception cause) {
        return new SearchableValidationException(MessageUtils.getMessage("scroll.cursor.invalid"), cause);
    }
}
