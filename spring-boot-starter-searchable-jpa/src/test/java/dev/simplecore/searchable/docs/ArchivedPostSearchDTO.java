package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.SearchableField;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;

// docs:start inherited
public class ArchivedPostSearchDTO extends TimestampSearchDTO {

    @SearchableField(operators = {CONTAINS})
    private String title;
}
// docs:end inherited
