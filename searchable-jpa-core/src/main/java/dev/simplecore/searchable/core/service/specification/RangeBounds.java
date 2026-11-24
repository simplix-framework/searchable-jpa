package dev.simplecore.searchable.core.service.specification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * Turns an inclusive upper bound that stands for "the last moment of a unit" into the exclusive
 * bound one step later.
 *
 * <p>The end of a day is written as 23:59:59.999999999. A driver that rounds a bound to the column's
 * precision carries that value into the next day, which would then be compared inclusively and
 * match rows stamped at exactly midnight. Comparing with {@code <} against the next instant means
 * the same thing at every precision.
 */
final class RangeBounds {

    private static final int LAST_NANO_OF_SECOND = 999_999_999;
    private static final int LAST_MILLI_OF_SECOND = 999;

    private RangeBounds() {
    }

    /**
     * @param upper an inclusive upper bound
     * @return the exclusive bound that means the same, or null when the bound is not the last
     *         representable moment of a second and is compared inclusively as given
     */
    static Comparable<?> exclusiveUpperBound(Object upper) {
        if (upper instanceof LocalDateTime value) {
            return value.getNano() == LAST_NANO_OF_SECOND && !value.equals(LocalDateTime.MAX)
                    ? value.plusNanos(1) : null;
        }
        if (upper instanceof Instant value) {
            return value.getNano() == LAST_NANO_OF_SECOND && !value.equals(Instant.MAX)
                    ? value.plusNanos(1) : null;
        }
        if (upper instanceof OffsetDateTime value) {
            return value.getNano() == LAST_NANO_OF_SECOND && !value.equals(OffsetDateTime.MAX)
                    ? value.plusNanos(1) : null;
        }
        if (upper instanceof ZonedDateTime value) {
            return value.getNano() == LAST_NANO_OF_SECOND
                    && !value.toLocalDateTime().equals(LocalDateTime.MAX) ? value.plusNanos(1) : null;
        }
        if (upper != null && upper.getClass() == Date.class) {
            long millis = ((Date) upper).getTime();
            return Math.floorMod(millis, 1000L) == LAST_MILLI_OF_SECOND && millis != Long.MAX_VALUE
                    ? new Date(millis + 1) : null;
        }
        return null;
    }
}
