package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.SearchableField;
import java.time.Instant;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.BETWEEN;

// docs:start base
public class TimestampSearchDTO {

    @SearchableField(operators = {BETWEEN}, sortable = true)
    private Instant createdAt;
}
// docs:end base
