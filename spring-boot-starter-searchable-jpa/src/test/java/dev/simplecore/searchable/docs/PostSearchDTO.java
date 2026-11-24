package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.CaseSensitivity;
import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.annotation.SearchableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.BETWEEN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.GREATER_THAN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.GREATER_THAN_OR_EQUAL_TO;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IS_NOT_NULL;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IS_NULL;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.LESS_THAN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.STARTS_WITH;

// docs:start basic
public class PostSearchDTO {

    @SearchableField(operators = {EQUALS, IN}, sortable = true)
    private Long id;

    // docs:start described
    @SearchableField(operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true, keyword = true)
    @Schema(description = "Post title", example = "Spring")
    @Size(min = 2, max = 100)
    private String title;
    // docs:end described

    @SearchableField(operators = {EQUALS, IN}, aggregatable = true)
    private PostStatus status;

    @SearchableField(operators = {GREATER_THAN_OR_EQUAL_TO, BETWEEN}, sortable = true, aggregatable = true)
    private Long viewCount;

    @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
    private Instant createdAt;
    // docs:end basic

    // docs:start nullable
    @SearchableField(operators = {IS_NULL, IS_NOT_NULL, BETWEEN})
    private LocalDateTime publishedAt;
    // docs:end nullable

    // docs:start related
    @SearchableField(entityField = "author.name", operators = {EQUALS, CONTAINS}, sortable = true,
            sortField = "author.familyName", keyword = true, caseSensitivity = CaseSensitivity.INSENSITIVE)
    private String authorName;
    // docs:end related

    // docs:start collection
    @SearchableField(entityField = "comments.content", operators = {CONTAINS})
    private String commentContent;

    @SearchableField(entityField = "comments.writer", operators = {EQUALS})
    private String commentWriter;

    @SearchableField(entityField = "comments.writer", operators = {EQUALS}, match = CollectionMatch.ANY)
    private String anyCommentWriter;

    @SearchableField(entityField = "comments.writer", operators = {EQUALS}, match = CollectionMatch.NONE)
    private String noCommentWriter;

    @SearchableField(entityField = "tags.name", operators = {EQUALS}, match = CollectionMatch.NONE)
    private String withoutTag;

    @SearchableField(entityField = "labels", operators = {EQUALS, IN})
    private String label;
    // docs:end collection
// docs:start basic
}
// docs:end basic
