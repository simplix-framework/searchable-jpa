package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.SearchableField;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

// docs:start record
public record PostQuery(
        @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true) String title,
        @SearchableField(operators = {EQUALS}) PostStatus status) {
}
// docs:end record
