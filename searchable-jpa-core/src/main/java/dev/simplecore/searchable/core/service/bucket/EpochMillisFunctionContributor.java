package dev.simplecore.searchable.core.service.bucket;

import java.util.Locale;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.CockroachDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.type.StandardBasicTypes;

/**
 * Teaches the query language to read an instant column as a plain count of milliseconds.
 *
 * <p>Dividing a window of time into slices needs the stored instant as a number, and there is no
 * portable way to ask for one. Hibernate's own temporal arithmetic is not it: on SQLite the JDBC
 * driver writes an instant as an integer of milliseconds while the dialect reads it back with
 * {@code strftime}, which answers null for a number, so the two halves of the same stack disagree
 * about the column they share.
 *
 * <p>So the expression is registered here, once per database, and it is the whole of what this
 * library knows about any dialect. A database with no entry is not broken by its absence:
 * {@link TimeBucketCounter} falls back to a slower form that needs no such function.
 *
 * <p>Each expression reads a column that carries its own offset as the instant it names. A column
 * without an offset is read as UTC wall-clock time, which is why {@link TimeBucketCounter} uses the
 * expression for such a column only when the values are written in UTC.
 *
 * <p>Registered through {@code META-INF/services}, so a host application gets it by having the
 * library on the classpath and never wires it itself.
 *
 * @see TimeBucketCounter
 */
public class EpochMillisFunctionContributor implements FunctionContributor {

    /** The function a query calls to read an instant column as milliseconds since the epoch. */
    public static final String FUNCTION_NAME = "epoch_millis";

    /** Seconds since the epoch with their fraction, turned into whole milliseconds. */
    private static final String EXTRACT_EPOCH = "cast(floor(extract(epoch from ?1) * 1000) as bigint)";

    /** The name fragment of the SQLite dialect, which ships outside Hibernate's own dialect classes. */
    private static final String SQLITE_DIALECT_NAME_FRAGMENT = "sqlite";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        String pattern = pattern(functionContributions.getDialect());
        if (pattern == null) {
            return;
        }
        functionContributions.getFunctionRegistry().registerPattern(
                FUNCTION_NAME,
                pattern,
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.LONG));
    }

    /**
     * @param dialect the database behind the host application
     * @return how that database turns an instant column into milliseconds since the epoch, or
     *         null when this library has no entry for it
     */
    static String pattern(Dialect dialect) {
        if (dialect instanceof PostgreSQLDialect || dialect instanceof CockroachDialect
                || dialect instanceof H2Dialect) {
            return EXTRACT_EPOCH;
        }
        if (dialect instanceof MySQLDialect) {
            // Plain date-time arithmetic: unlike unix_timestamp() it does not depend on the
            // session time zone.
            return "(timestampdiff(microsecond, '1970-01-01 00:00:00', ?1) div 1000)";
        }
        if (dialect instanceof SQLServerDialect) {
            return "datediff_big(millisecond, cast('1970-01-01' as datetime2), ?1)";
        }
        // The driver already stores the instant as an integer of milliseconds, so the column is
        // itself the answer; a datetime function here would be reading a number as a date.
        if (dialect.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(SQLITE_DIALECT_NAME_FRAGMENT)) {
            return "?1";
        }
        // Oracle would need a cast to DATE, which loses fractional seconds; it and every database
        // without an entry use the precise conditional sums.
        return null;
    }
}
