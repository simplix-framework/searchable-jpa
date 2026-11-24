package dev.simplecore.searchable.starter;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;

public class ThingSearchDto {

    @SearchableField(operators = {SearchOperator.EQUALS}, sortable = true)
    private Long id;

    @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.CONTAINS, SearchOperator.IS_NULL}, sortable = true)
    private String name;

    @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN})
    private Thing.Kind kind;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Thing.Kind getKind() {
        return kind;
    }

    public void setKind(Thing.Kind kind) {
        this.kind = kind;
    }
}
