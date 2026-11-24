package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/**
 * Search DTO that offers some of its fields for aggregation and keeps others for searching only.
 */
@Getter
@Setter
public class TestPostStatsDTO {

    @SearchableField(operators = {EQUALS}, aggregatable = true)
    private TestPostStatus status;

    @SearchableField(entityField = "viewCount", aggregatable = true)
    private Long views;

    @SearchableField(operators = {EQUALS})
    private String title;
}
