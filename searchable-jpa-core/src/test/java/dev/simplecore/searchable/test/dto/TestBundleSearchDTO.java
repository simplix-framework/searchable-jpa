package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.CaseSensitivity;
import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IS_NULL;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.NOT_EQUALS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.NOT_IN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.STARTS_WITH;

/**
 * Search DTO over {@code TestBundle}: the same column compared as the database compares it, without
 * regard to case and with regard to case, and the collection of labels matched by joining it, by
 * some element and by no element.
 */
@Getter
@Setter
public class TestBundleSearchDTO {

    @SearchableField(operators = {EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, STARTS_WITH}, sortable = true)
    private String name;

    @SearchableField(entityField = "name", operators = {EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, STARTS_WITH},
            caseSensitivity = CaseSensitivity.INSENSITIVE)
    private String nameIgnoringCase;

    @SearchableField(entityField = "name", operators = {EQUALS, CONTAINS}, caseSensitivity = CaseSensitivity.SENSITIVE)
    private String nameExactly;

    @SearchableField(entityField = "labels", operators = {EQUALS, CONTAINS, IN, IS_NULL})
    private String label;

    @SearchableField(entityField = "labels", operators = {EQUALS, CONTAINS, IN}, match = CollectionMatch.ANY)
    private String anyLabel;

    @SearchableField(entityField = "labels", operators = {EQUALS, CONTAINS, IN}, match = CollectionMatch.NONE,
            caseSensitivity = CaseSensitivity.INSENSITIVE)
    private String noLabel;
}
