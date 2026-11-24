package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/**
 * Search DTO that asks about the comments of a post one condition at a time: some comment
 * satisfies it, or none does.
 */
@Getter
@Setter
public class TestPostQuantifierDTO {

    @SearchableField(entityField = "comments.content", operators = {CONTAINS}, match = CollectionMatch.ANY)
    private String anyComment;

    @SearchableField(entityField = "comments.author.name", operators = {EQUALS}, match = CollectionMatch.ANY)
    private String anyCommentBy;

    @SearchableField(entityField = "comments.content", operators = {CONTAINS}, match = CollectionMatch.NONE)
    private String noComment;

    @SearchableField(entityField = "comments.author.name", operators = {EQUALS}, match = CollectionMatch.NONE)
    private String noCommentBy;

    @SearchableField(entityField = "comments.content", operators = {CONTAINS})
    private String comment;

    @SearchableField(entityField = "comments.author.name", operators = {EQUALS})
    private String commentBy;

    @SearchableField(entityField = "author.name", operators = {EQUALS})
    private String authorName;
}
