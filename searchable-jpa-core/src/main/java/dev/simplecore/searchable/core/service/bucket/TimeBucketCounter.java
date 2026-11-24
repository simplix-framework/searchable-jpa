package dev.simplecore.searchable.core.service.bucket;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.specification.MatchingRows;
import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Counts rows per slice of a time window, under the same conditions the list beside them is
 * read with.
 *
 * <p>A time-range selector draws a strip of how busy each slice of a window was, and the strip
 * has to agree with the list under it: narrowing to one customer must thin the strip too. So
 * the count is built from the screen's own {@link SearchCondition} rather than from a second set
 * of parameters that could drift from it.
 *
 * <p>Counted in the database. A window can hold far more rows than are worth carrying into
 * memory to divide by time, and counting is what a database is for.
 *
 * <h2>The time axis</h2>
 *
 * <p>The axis is an {@code Instant}, an {@code OffsetDateTime}, a {@code ZonedDateTime} or a
 * {@code LocalDateTime} attribute. A {@code LocalDateTime} names no instant by itself and is read
 * in the application's time zone ({@link SearchableTimeZoneHolder}), the zone search values without
 * one are read in.
 *
 * <h2>Two forms, and why</h2>
 *
 * <p>Where {@link EpochMillisFunctionContributor} could express a stored instant as a number,
 * an evenly divided window is counted by one {@code group by} over an arithmetic bucket: the
 * database reads each row in the window once and does one division on it. That is the form that
 * holds up as a table grows.
 *
 * <p>Everything else is counted by one conditional sum per slice: a database this library has no
 * entry for, a column whose values the number would misplace, an axis that is not an
 * {@code Instant}, and slices of unequal width such as calendar months. Correct, needs nothing but
 * comparison, and costs a comparison per row per slice.
 *
 * <p>Either way the window wants an index on the field it is measured over. Without one, every
 * strip is a full scan of the table no matter which form runs.
 */
public class TimeBucketCounter {

    /**
     * The most slices one call may ask for, unless the persistence unit sets
     * {@link SearchableJpaSettings#MAX_BUCKETS}.
     *
     * <p>A strip is read by eye and there is no reading a thousand columns of it. The ceiling
     * also bounds the width of the conditional statement, which carries one sum per slice.
     */
    public static final int MAX_BUCKETS = 512;

    /**
     * One slice of a window cut at calendar boundaries.
     *
     * @param from the first instant of the slice, inclusive
     * @param to the instant the slice ends at, exclusive
     * @param count the matching rows in it
     */
    public record TimeBucket(Instant from, Instant to, long count) {
    }

    private final EntityManager entityManager;

    /**
     * @param entityManager where the query is run
     */
    public TimeBucketCounter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Counts matching rows per bucket across a window.
     *
     * @param entityType the entity being counted
     * @param repository the entity's repository
     * @param condition what the screen is currently filtered by
     * @param timeAxisField the entity field the window is measured on
     * @param from the first instant in the window, inclusive
     * @param to the instant the window ends at, exclusive
     * @param buckets how many slices to divide the window into
     * @param <T> the entity type
     * @return one count per bucket, oldest first, always {@code buckets} long
     * @throws SearchableValidationException when the window is empty or reversed, the bucket count
     *         is outside the allowed range, or the field is not a time attribute
     * @see #count(Class, SearchCondition, Specification, String, Instant, Instant, int)
     */
    public <T> List<Long> count(Class<T> entityType,
                                JpaSpecificationExecutor<T> repository,
                                SearchCondition<?> condition,
                                String timeAxisField,
                                Instant from, Instant to, int buckets) {
        return count(entityType, condition, null, timeAxisField, from, to, buckets);
    }

    /**
     * Counts matching rows per bucket across a window divided into equal slices.
     *
     * <p>The window is half-open: a row at exactly {@code to} belongs to the next window, so
     * scrubbing from one window to the next never counts a row twice. Every slice is half-open
     * the same way, so a row on a boundary is counted once, by the later slice.
     *
     * @param entityType the entity being counted
     * @param condition what the screen is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param timeAxisField the entity field the window is measured on
     * @param from the first instant in the window, inclusive
     * @param to the instant the window ends at, exclusive
     * @param buckets how many slices to divide the window into
     * @param <T> the entity type
     * @return one count per bucket, oldest first, always {@code buckets} long
     * @throws SearchableValidationException when the window is empty or reversed, the bucket count
     *         is outside the allowed range, or the field is not a time attribute
     */
    public <T> List<Long> count(Class<T> entityType,
                                SearchCondition<?> condition,
                                Specification<T> scope,
                                String timeAxisField,
                                Instant from, Instant to, int buckets) {
        requireWindow(from, to);
        requireBucketCount(buckets);
        Class<?> axisType = requireTimeAxis(entityType, timeAxisField);

        if (axisType == Instant.class && canUseGrouping(from, to, buckets) && canReadInstantAsNumber()
                && storesTheInstantItNames(entityType, timeAxisField)) {
            return countByGrouping(entityType, condition, scope, timeAxisField, from, to, buckets);
        }
        return boxed(countBetween(entityType, condition, scope, timeAxisField, axisType, edges(from, to, buckets)));
    }

    /**
     * Counts matching rows per calendar slice of a window: per hour, day, week, month or year as a
     * clock and a calendar in the given zone show them.
     *
     * <p>The window is cut at every boundary of the unit that falls inside it, so the first and the
     * last slice are shorter when the window does not start or end on a boundary, and a day in a
     * zone that changes its clocks is as long as that day was. Weeks start on Monday.
     *
     * @param entityType the entity being counted
     * @param condition what the screen is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param timeAxisField the entity field the window is measured on
     * @param from the first instant in the window, inclusive
     * @param to the instant the window ends at, exclusive
     * @param unit {@code HOURS}, {@code DAYS}, {@code WEEKS}, {@code MONTHS} or {@code YEARS}
     * @param zone the zone whose calendar the slices follow
     * @param <T> the entity type
     * @return the slices, oldest first, each with its bounds and its count
     * @throws SearchableValidationException when the window is empty or reversed, the unit is not a
     *         calendar unit, the window holds more slices than allowed, or the field is not a time
     *         attribute
     */
    public <T> List<TimeBucket> countByCalendar(Class<T> entityType,
                                                SearchCondition<?> condition,
                                                Specification<T> scope,
                                                String timeAxisField,
                                                Instant from, Instant to,
                                                ChronoUnit unit, ZoneId zone) {
        requireWindow(from, to);
        Class<?> axisType = requireTimeAxis(entityType, timeAxisField);
        Instant[] edges = calendarEdges(from, to, unit, zone);

        long[] counts = countBetween(entityType, condition, scope, timeAxisField, axisType, edges);
        List<TimeBucket> slices = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            slices.add(new TimeBucket(edges[i], edges[i + 1], counts[i]));
        }
        return slices;
    }

    /**
     * One pass over the window, one division per row.
     *
     * <p>Elapsed time is multiplied by the bucket count before being divided by the window, so a
     * window that does not divide evenly still produces slices of even width. Dividing first
     * would round the width down and leave the last slice absorbing the remainder.
     */
    private <T> List<Long> countByGrouping(Class<T> entityType,
                                           SearchCondition<?> condition,
                                           Specification<T> scope,
                                           String timeAxisField,
                                           Instant from, Instant to, int buckets) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        Expression<Instant> axis = root.get(timeAxisField);

        long start = from.toEpochMilli();
        long span = to.toEpochMilli() - start;

        Expression<Long> millis = cb.function(
                EpochMillisFunctionContributor.FUNCTION_NAME, Long.class, axis);
        Expression<Number> scaled = cb.prod(cb.diff(millis, start), (long) buckets);
        Expression<Number> bucket = cb.floor(cb.quot(scaled, span));

        query.multiselect(bucket.alias("bucket"), cb.count(root).alias("total"))
                .where(window(cb, query, root, entityType, condition, scope, axis, from, to))
                .groupBy(bucket);

        long[] counts = new long[buckets];
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            Number index = row.get("bucket", Number.class);
            Number total = row.get("total", Number.class);
            if (index == null || total == null) {
                continue;
            }
            int slot = index.intValue();
            // The predicates cannot let a row fall outside the window, but a rounding edge would
            // corrupt memory rather than the answer if one ever did.
            if (slot >= 0 && slot < buckets) {
                counts[slot] = total.longValue();
            }
        }
        return boxed(counts);
    }

    /**
     * One conditional sum per slice, in one statement.
     *
     * @param edges the instants the slices begin and end at, one more than there are slices
     * @return one count per slice
     */
    private <T> long[] countBetween(Class<T> entityType,
                                    SearchCondition<?> condition,
                                    Specification<T> scope,
                                    String timeAxisField, Class<?> axisType,
                                    Instant[] edges) {
        int slices = edges.length - 1;
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        Expression<?> axis = root.get(timeAxisField);

        List<Selection<?>> sums = new ArrayList<>(slices);
        for (int i = 0; i < slices; i++) {
            Expression<Long> counted = cb.<Long>selectCase()
                    .when(between(cb, axis, bound(edges[i], axisType), bound(edges[i + 1], axisType)), 1L)
                    .otherwise(0L)
                    .as(Long.class);
            sums.add(cb.sum(counted).alias(alias(i)));
        }

        query.multiselect(sums).where(window(cb, query, root, entityType, condition, scope, axis,
                bound(edges[0], axisType), bound(edges[slices], axisType)));

        Tuple row = entityManager.createQuery(query).getSingleResult();
        long[] counts = new long[slices];
        for (int i = 0; i < slices; i++) {
            // A sum over no rows at all is null, which is a window nothing happened in.
            Number total = row.get(alias(i), Number.class);
            counts[i] = total == null ? 0L : total.longValue();
        }
        return counts;
    }

    /** An instant as a value of the axis attribute's type. */
    private static Comparable<?> bound(Instant instant, Class<?> axisType) {
        if (axisType == LocalDateTime.class) {
            return LocalDateTime.ofInstant(instant, SearchableTimeZoneHolder.getZoneId());
        }
        if (axisType == OffsetDateTime.class) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (axisType == ZonedDateTime.class) {
            return instant.atZone(ZoneOffset.UTC);
        }
        return instant;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate between(CriteriaBuilder cb, Expression axis, Comparable lower, Comparable upper) {
        return cb.and(cb.greaterThanOrEqualTo(axis, lower), cb.lessThan(axis, upper));
    }

    /**
     * The window's own bounds, and whatever the screen is filtered by on top of them.
     */
    private <T> Predicate window(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                 Class<T> entityType, SearchCondition<?> condition, Specification<T> scope,
                                 Expression<?> axis, Comparable<?> from, Comparable<?> to) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(between(cb, axis, from, to));
        MatchingRows.of(cb, query, root, entityType, entityManager, condition, scope)
                .ifPresent(conditions::add);
        return cb.and(conditions.toArray(new Predicate[0]));
    }

    /**
     * @return whether this database has an entry in {@link EpochMillisFunctionContributor}
     */
    private boolean canReadInstantAsNumber() {
        SqmFunctionRegistry registry = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class)
                .getQueryEngine()
                .getSqmFunctionRegistry();
        SqmFunctionDescriptor descriptor =
                registry.findFunctionDescriptor(EpochMillisFunctionContributor.FUNCTION_NAME);
        return descriptor != null;
    }

    /**
     * The instants the slices begin and end at, {@code buckets + 1} of them.
     *
     * <p>Each edge is computed from the whole window rather than by adding a rounded width, so a
     * window that does not divide evenly spreads the remainder across the slices instead of
     * letting the last one absorb it. The final edge is the window's own end exactly.
     */
    private Instant[] edges(Instant from, Instant to, int buckets) {
        BigInteger span = nanosBetween(from, to);
        BigInteger divisor = BigInteger.valueOf(buckets);
        Instant[] edges = new Instant[buckets + 1];
        for (int i = 0; i < buckets; i++) {
            BigInteger offset = span.multiply(BigInteger.valueOf(i))
                    .add(divisor.subtract(BigInteger.ONE)).divide(divisor);
            BigInteger[] parts = offset.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
            edges[i] = from.plusSeconds(parts[0].longValueExact()).plusNanos(parts[1].longValueExact());
        }
        edges[buckets] = to;
        return edges;
    }

    /**
     * The window's bounds and every boundary of the unit between them, in the zone's calendar.
     */
    private Instant[] calendarEdges(Instant from, Instant to, ChronoUnit unit, ZoneId zone) {
        int most = maxBuckets();
        List<Instant> edges = new ArrayList<>();
        edges.add(from);
        ZonedDateTime boundary = startOf(from.atZone(zone), unit).plus(1, unit);
        while (boundary.toInstant().isBefore(to)) {
            if (edges.size() >= most) {
                throw new SearchableValidationException(
                        MessageUtils.getMessage("bucket.count.invalid", new Object[]{String.valueOf(most)}));
            }
            edges.add(boundary.toInstant());
            boundary = boundary.plus(1, unit);
        }
        edges.add(to);
        return edges.toArray(new Instant[0]);
    }

    private static ZonedDateTime startOf(ZonedDateTime time, ChronoUnit unit) {
        ZonedDateTime day = time.truncatedTo(ChronoUnit.DAYS);
        return switch (unit) {
            case HOURS -> time.truncatedTo(ChronoUnit.HOURS);
            case DAYS -> day;
            case WEEKS -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHS -> day.withDayOfMonth(1);
            case YEARS -> day.withDayOfYear(1);
            default -> throw new SearchableValidationException(
                    MessageUtils.getMessage("bucket.unit.unsupported", new Object[]{unit}));
        };
    }

    private BigInteger nanosBetween(Instant from, Instant to) {
        Duration duration = Duration.between(from, to);
        return BigInteger.valueOf(duration.getSeconds()).multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(duration.getNano()));
    }

    private boolean canUseGrouping(Instant from, Instant to, int buckets) {
        // Arithmetic milliseconds are exact only at millisecond-aligned bucket edges.
        // The conditional path retains Instant precision for all other windows.
        if (from.getNano() % 1_000_000 != 0 || to.getNano() % 1_000_000 != 0) {
            return false;
        }
        BigInteger span = nanosBetween(from, to).divide(BigInteger.valueOf(1_000_000));
        return span.signum() > 0 && span.mod(BigInteger.valueOf(buckets)).signum() == 0
                && span.multiply(BigInteger.valueOf(buckets)).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }

    private static List<Long> boxed(long[] counts) {
        List<Long> result = new ArrayList<>(counts.length);
        for (long count : counts) {
            result.add(count);
        }
        return result;
    }

    private static String alias(int bucket) {
        return "b" + bucket;
    }

    private int maxBuckets() {
        return SearchableJpaSettings.positiveInt(entityManager, SearchableJpaSettings.MAX_BUCKETS, MAX_BUCKETS);
    }

    private void requireWindow(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new SearchableValidationException(MessageUtils.getMessage("bucket.window.invalid"));
        }
    }

    private void requireBucketCount(int buckets) {
        int most = maxBuckets();
        if (buckets < 1 || buckets > most) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("bucket.count.invalid", new Object[]{String.valueOf(most)}));
        }
    }

    /** @return the type of the axis attribute, one of the time types a window can be measured on */
    private <T> Class<?> requireTimeAxis(Class<T> entityType, String timeAxisField) {
        ManagedType<T> type = entityManager.getMetamodel().managedType(entityType);
        Attribute<? super T, ?> attribute;
        try {
            attribute = type.getAttribute(timeAxisField);
        } catch (IllegalArgumentException e) {
            throw new SearchableValidationException(MessageUtils.getMessage("aggregate.field.unknown",
                    new Object[]{timeAxisField, entityType.getSimpleName()}), e);
        }
        Class<?> axisType = attribute.getJavaType();
        if (axisType != Instant.class && axisType != LocalDateTime.class
                && axisType != OffsetDateTime.class && axisType != ZonedDateTime.class) {
            throw new SearchableValidationException(MessageUtils.getMessage("bucket.axis.type",
                    new Object[]{timeAxisField, axisType.getSimpleName()}));
        }
        return axisType;
    }

    /**
     * Whether the number {@link EpochMillisFunctionContributor} reads from the column is the instant
     * that was stored.
     *
     * <p>A column that keeps an offset names its instant whatever zone wrote it. A column without one
     * holds wall-clock time in the zone the driver binds with, and reads back as the right number
     * only when that zone is UTC. Anywhere else the number would be off by the zone's offset and
     * every row would land in the wrong slice, so such a column is counted by conditional sums.
     */
    private <T> boolean storesTheInstantItNames(Class<T> entityType, String timeAxisField) {
        SessionFactoryImplementor factory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class);
        AttributeMapping mapping = factory.getMappingMetamodel()
                .getEntityDescriptor(entityType)
                .findAttributeMapping(timeAxisField);
        if (!(mapping instanceof BasicValuedModelPart column)) {
            return false;
        }
        int sqlType = column.getJdbcMapping().getJdbcType().getDefaultSqlTypeCode();
        if (sqlType == SqlTypes.TIMESTAMP_UTC || sqlType == SqlTypes.TIMESTAMP_WITH_TIMEZONE) {
            return true;
        }
        TimeZone configured = factory.getSessionFactoryOptions().getJdbcTimeZone();
        TimeZone bindingZone = configured != null ? configured : TimeZone.getDefault();
        return bindingZone.getRawOffset() == 0 && !bindingZone.useDaylightTime();
    }
}
