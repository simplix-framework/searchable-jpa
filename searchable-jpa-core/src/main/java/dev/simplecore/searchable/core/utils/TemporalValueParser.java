package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Locale;

/**
 * Parses the temporal types of {@link SearchableValueParser}.
 *
 * <p>Every pattern resolves strictly, so a date the calendar does not have (February 31st) is
 * refused instead of being moved to the nearest valid day.
 */
final class TemporalValueParser {

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            strict("uuuu-MM-dd HH:mm:ss"),
            strict("uuuu/MM/dd HH:mm:ss"),
            strict("dd-MM-uuuu HH:mm:ss"),
            strict("dd/MM/uuuu HH:mm:ss"),
            strict("uuuu.MM.dd HH:mm:ss"),
            strict("uuuuMMddHHmmss"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSS"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSSSSS"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")
    };

    private static final DateTimeFormatter[] TIMEZONE_AWARE_FORMATTERS = {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            strict("uuuu-MM-dd'T'HH:mm:ssXXX"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSSXXX"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSSSSSXXX"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX"),
            strict("uuuu-MM-dd'T'HH:mm:ssZ"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSSZ"),
            strict("uuuu-MM-dd HH:mm:ss XXX"),
            strict("uuuu-MM-dd HH:mm:ss Z"),
            DateTimeFormatter.RFC_1123_DATE_TIME,
            // RFC 3339 with one or two fraction digits
            strict("uuuu-MM-dd'T'HH:mm:ss.SX"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSX"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SZ"),
            strict("uuuu-MM-dd'T'HH:mm:ss.SSZ"),
            // Space separator with an offset
            strict("uuuu-MM-dd HH:mm:ssXXX"),
            strict("uuuu-MM-dd HH:mm:ss.SSSXXX"),
            strict("uuuu-MM-dd HH:mm:ssZ"),
            strict("uuuu-MM-dd HH:mm:ss.SSSZ")
    };

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            strict("uuuu/MM/dd"),
            strict("dd-MM-uuuu"),
            strict("dd/MM/uuuu"),
            strict("uuuuMMdd"),
            strict("uuuu.MM.dd"),
            DateTimeFormatter.BASIC_ISO_DATE
    };

    private static final DateTimeFormatter[] TIME_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_TIME,
            strict("HH:mm:ss.SSS"),
            strict("HHmmss"),
            strict("HHmm"),
            strict("H:mm")
    };

    private TemporalValueParser() {
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    private static ZoneId appZone() {
        return SearchableTimeZoneHolder.getZoneId();
    }

    static boolean isTemporal(Class<?> type) {
        return Temporal.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)
                || type == MonthDay.class;
    }

    /**
     * @param value the text to read
     * @param targetType the temporal type wanted
     * @param rangeEnd null for a plain value; otherwise whether the value closes a range, in which
     *                 case a value without a time of day is widened to the end of that day
     */
    static Object parse(String value, Class<?> targetType, Boolean rangeEnd) {
        try {
            if (targetType == LocalDateTime.class) {
                return atDayBoundary(value, rangeEnd, localDateTime(value));
            }
            if (targetType == LocalDate.class) {
                return localDate(value);
            }
            if (targetType == LocalTime.class) {
                return localTime(value);
            }
            if (targetType == ZonedDateTime.class) {
                LocalDateTime boundary = dayBoundary(value, rangeEnd);
                return boundary != null ? boundary.atZone(appZone()) : zonedDateTime(value);
            }
            if (targetType == OffsetDateTime.class) {
                LocalDateTime boundary = dayBoundary(value, rangeEnd);
                return boundary != null ? boundary.atZone(appZone()).toOffsetDateTime() : offsetDateTime(value);
            }
            if (targetType == Instant.class) {
                LocalDateTime boundary = dayBoundary(value, rangeEnd);
                return boundary != null ? boundary.atZone(appZone()).toInstant() : instant(value);
            }
            if (targetType == Date.class) {
                LocalDateTime boundary = dayBoundary(value, rangeEnd);
                return boundary != null ? Date.from(boundary.atZone(appZone()).toInstant()) : date(value);
            }
            if (targetType == Year.class) {
                return Year.parse(value);
            }
            if (targetType == YearMonth.class) {
                return YearMonth.parse(value);
            }
            if (targetType == MonthDay.class) {
                return MonthDay.parse(value);
            }
        } catch (DateTimeException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.temporal.parse.failed",
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
        throw new SearchableParseException(
                MessageUtils.getMessage("parser.temporal.type.unsupported", new Object[]{targetType.getSimpleName()}));
    }

    private static LocalDateTime atDayBoundary(String value, Boolean rangeEnd, LocalDateTime parsed) {
        LocalDateTime boundary = dayBoundary(value, rangeEnd);
        return boundary != null ? boundary : parsed;
    }

    /**
     * @return the first or last instant of the day when the value is a range bound that names a day
     *         without a time, otherwise null
     */
    private static LocalDateTime dayBoundary(String value, Boolean rangeEnd) {
        if (rangeEnd == null) {
            return null;
        }
        LocalDate day = tryLocalDate(value);
        if (day == null) {
            return null;
        }
        return rangeEnd ? day.atTime(LocalTime.MAX) : day.atTime(LocalTime.MIN);
    }

    private static LocalDate tryLocalDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // not this format
            }
        }
        return null;
    }

    private static LocalDate localDate(String value) {
        LocalDate date = tryLocalDate(value);
        if (date == null) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.date.format.unsupported", new Object[]{value}));
        }
        return date;
    }

    private static LocalTime localTime(String value) {
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // not this format
            }
        }
        throw new SearchableParseException(
                MessageUtils.getMessage("parser.time.format.unsupported", new Object[]{value}));
    }

    private static LocalDateTime localDateTime(String value) {
        // A value carrying a zone or an offset names an instant; it is read in the application zone.
        if (hasTimezoneInfo(value)) {
            return zoneAwareLocalDateTime(value);
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // not this format
            }
        }
        LocalDate date = tryLocalDate(value);
        if (date != null) {
            return date.atStartOfDay();
        }
        throw new SearchableParseException(
                MessageUtils.getMessage("parser.datetime.format.unsupported", new Object[]{value}));
    }

    private static boolean hasTimezoneInfo(String value) {
        return value.endsWith("Z")
                || value.matches(".*T\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?[+-]\\d{2}:?\\d{2}$")
                || value.matches(".*\\s\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?[+-]\\d{2}:?\\d{2}$")
                || value.matches(".*T\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?[+-]\\d{4}$")
                || value.contains("GMT")
                || value.contains("UTC")
                || value.matches(".*\\s[A-Z]{3,4}$");
    }

    private static LocalDateTime zoneAwareLocalDateTime(String value) {
        Instant instant = tryZoneAwareInstant(value);
        if (instant == null) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.datetime.timezone.failed", new Object[]{value}));
        }
        return instant.atZone(appZone()).toLocalDateTime();
    }

    private static Instant tryZoneAwareInstant(String value) {
        if (value.endsWith("Z")) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                // a literal Z in a format Instant.parse does not read
            }
        }
        for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
            try {
                return OffsetDateTime.parse(value, formatter).toInstant();
            } catch (DateTimeParseException offsetFailure) {
                try {
                    return ZonedDateTime.parse(value, formatter).toInstant();
                } catch (DateTimeParseException ignored) {
                    // not this format
                }
            }
        }
        return null;
    }

    private static ZonedDateTime zonedDateTime(String value) {
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // not the ISO zoned form
        }
        if (hasTimezoneInfo(value)) {
            try {
                return OffsetDateTime.parse(value).toZonedDateTime();
            } catch (DateTimeParseException ignored) {
                // not the ISO offset form
            }
            for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
                try {
                    return ZonedDateTime.parse(value, formatter);
                } catch (DateTimeParseException ignored) {
                    // not this format
                }
            }
        }
        try {
            return localDateTime(value).atZone(appZone());
        } catch (SearchableParseException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.zoneddatetime.invalid", new Object[]{value}), e);
        }
    }

    private static OffsetDateTime offsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // not the ISO offset form
        }
        if (hasTimezoneInfo(value)) {
            for (DateTimeFormatter formatter : TIMEZONE_AWARE_FORMATTERS) {
                try {
                    return OffsetDateTime.parse(value, formatter);
                } catch (DateTimeParseException ignored) {
                    // not this format
                }
            }
            try {
                return ZonedDateTime.parse(value).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // not the ISO zoned form
            }
        }
        try {
            return localDateTime(value).atZone(appZone()).toOffsetDateTime();
        } catch (SearchableParseException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.offsetdatetime.invalid", new Object[]{value}), e);
        }
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // not the ISO instant form
        }
        if (hasTimezoneInfo(value)) {
            Instant zoneAware = tryZoneAwareInstant(value);
            if (zoneAware != null) {
                return zoneAware;
            }
        }
        try {
            return localDateTime(value).atZone(appZone()).toInstant();
        } catch (SearchableParseException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.instant.invalid", new Object[]{value}), e);
        }
    }

    private static Date date(String value) {
        try {
            return Date.from(instant(value));
        } catch (SearchableParseException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.date.invalid", new Object[]{value}), e);
        }
    }
}
