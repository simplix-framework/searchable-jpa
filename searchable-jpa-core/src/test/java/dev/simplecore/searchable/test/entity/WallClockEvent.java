package dev.simplecore.searchable.test.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An event whose instant is kept in a column without an offset, the way a schema written before
 * offset-aware column types keeps it. The column type is part of the mapping, so the table is the
 * same in every test context and on every database.
 */
@Entity
public class WallClockEvent {

    @Id
    @GeneratedValue
    public Long id;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant occurredAt;

    /** The same moment as a local date and time, which names an instant only together with a zone. */
    public LocalDateTime localTime;

    /** The same moment with the offset it was recorded in. */
    public OffsetDateTime offsetTime;

    public String source;
}
