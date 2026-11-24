package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.annotation.CaseSensitivity;
import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A search DTO is read by its fields. A record, a class with accessors and a class without any
 * declare the same thing and are validated the same way, on every path a condition arrives by.
 */
class DtoShapeTest {

    public record RecordDto(
            @SearchableField(entityField = "title", operators = {EQUALS, CONTAINS}, sortable = true)
            @Size(min = 2, message = "label needs two characters") String label,
            @SearchableField(operators = {EQUALS}) Long amount,
            String notSearchable) {
    }

    public static class FieldOnlyDto {
        @SearchableField(entityField = "title", operators = {EQUALS, CONTAINS}, sortable = true)
        @Size(min = 2, message = "label needs two characters")
        private String label;
        @SearchableField(operators = {EQUALS})
        private Long amount;
        private String notSearchable;
    }

    public static class DeclarationDto {
        @SearchableField(entityField = "code", operators = {EQUALS}, caseSensitivity = CaseSensitivity.INSENSITIVE)
        private String code;
        @SearchableField(entityField = "tags.name", operators = {EQUALS}, match = CollectionMatch.NONE)
        private String withoutTag;
        @SearchableField(operators = {EQUALS}, caseSensitivity = CaseSensitivity.SENSITIVE)
        private String exact;
        @SearchableField(operators = {EQUALS})
        private String plain;
    }

    @BeforeEach
    void english() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static SearchCondition.Condition first(SearchCondition<?> condition) {
        return (SearchCondition.Condition) condition.getNodes().get(0);
    }

    private static <D> void acceptsEveryPath(Class<D> dto) throws Exception {
        SearchCondition<D> built = SearchConditionBuilder.create(dto)
                .where(w -> w.contains("label", "ab").equals("amount", 5L))
                .sort(s -> s.asc("label")).page(0).size(10).build();
        assertThat(first(built).getEntityField()).isEqualTo("title");
        assertThat(built.getSort().getOrders().get(0).getEntityField()).isEqualTo("title");

        SearchCondition<D> parsed = new SearchableParamsParser<>(dto)
                .convert(Map.of("label.contains", "ab", "amount.equals", "5", "sort", "label.asc"));
        assertThat(parsed.getNodes()).hasSize(2);

        SearchCondition<D> json = SearchCondition.fromJson(
                "{\"conditions\":[{\"field\":\"label\",\"searchOperator\":\"equals\",\"value\":\"ab\"}],"
                        + "\"sort\":{\"orders\":[{\"field\":\"label\",\"direction\":\"desc\"}]}}", dto);
        assertThat(first(json).getEntityField()).isEqualTo("title");
    }

    private static <D> void refusesWhatItDoesNotDeclare(Class<D> dto) {
        assertThatThrownBy(() -> SearchConditionBuilder.create(dto).where(w -> w.equals("notSearchable", "x")).build())
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("notSearchable");
        assertThatThrownBy(() -> SearchConditionBuilder.create(dto).where(w -> w.equals("missing", "x")).build())
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> SearchConditionBuilder.create(dto).where(w -> w.contains("amount", "5")).build())
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> SearchConditionBuilder.create(dto).where(w -> w.equals("amount", 5L))
                .sort(s -> s.asc("amount")).build())
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("amount");
        // A constraint on the field applies to the value searched for.
        assertThatThrownBy(() -> SearchConditionBuilder.create(dto).where(w -> w.contains("label", "a")).build())
                .isInstanceOf(SearchableValidationException.class).hasMessage("label needs two characters");
    }

    @Test
    @DisplayName("a record is a search DTO")
    void recordDto() throws Exception {
        acceptsEveryPath(RecordDto.class);
        refusesWhatItDoesNotDeclare(RecordDto.class);
    }

    @Test
    @DisplayName("a class without accessors is a search DTO")
    void fieldOnlyDto() throws Exception {
        acceptsEveryPath(FieldOnlyDto.class);
        refusesWhatItDoesNotDeclare(FieldOnlyDto.class);
    }

    @Test
    @DisplayName("the page size may be sent without a page number, and a page number without a size")
    void pageAndSizeAreIndependent() {
        assertThat(new SearchableParamsParser<>(FieldOnlyDto.class).convert(Map.of("size", "10")).getSize()).isEqualTo(10);
        assertThat(new SearchableParamsParser<>(FieldOnlyDto.class).convert(Map.of("page", "2")).getPage()).isEqualTo(2);
        assertThat(SearchConditionBuilder.create(FieldOnlyDto.class).size(5).build().getPage()).isNull();
        assertThatThrownBy(() -> SearchConditionBuilder.create(FieldOnlyDto.class).size(0).build())
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> SearchConditionBuilder.create(FieldOnlyDto.class).page(-1).build())
                .isInstanceOf(SearchableValidationException.class);
    }

    @Test
    @DisplayName("what the DTO declares about case and collections travels with the condition, and only from the DTO")
    void declarationsTravelWithTheCondition() throws Exception {
        SearchCondition<DeclarationDto> built = SearchConditionBuilder.create(DeclarationDto.class)
                .where(w -> w.equals("code", "ab").equals("withoutTag", "draft").equals("exact", "x").equals("plain", "y"))
                .build();
        SearchCondition.Condition code = (SearchCondition.Condition) built.getNodes().get(0);
        SearchCondition.Condition withoutTag = (SearchCondition.Condition) built.getNodes().get(1);
        SearchCondition.Condition exact = (SearchCondition.Condition) built.getNodes().get(2);
        SearchCondition.Condition plain = (SearchCondition.Condition) built.getNodes().get(3);

        assertThat(code.getCaseInsensitive()).isTrue();
        assertThat(code.getMatch()).isNull();
        assertThat(withoutTag.getMatch()).isEqualTo(CollectionMatch.NONE);
        assertThat(withoutTag.getEntityField()).isEqualTo("tags.name");
        assertThat(exact.getCaseInsensitive()).isFalse();
        assertThat(plain.getCaseInsensitive()).isNull();
        assertThat(plain.getMatch()).isNull();

        SearchCondition.Condition fromParams = first(new SearchableParamsParser<>(DeclarationDto.class)
                .convert(Map.of("code.equals", "ab")));
        assertThat(fromParams.getCaseInsensitive()).isTrue();

        // A request cannot switch these on for a field that does not declare them, nor name a path.
        SearchCondition.Condition fromJson = first(SearchCondition.fromJson(
                "{\"conditions\":[{\"field\":\"plain\",\"searchOperator\":\"equals\",\"value\":\"y\","
                        + "\"caseInsensitive\":true,\"match\":\"NONE\",\"entityField\":\"secret\"}]}", DeclarationDto.class));
        assertThat(fromJson.getCaseInsensitive()).isNull();
        assertThat(fromJson.getMatch()).isNull();
        assertThat(fromJson.getEntityField()).isEqualTo("plain");
        assertThat(built.toJson()).doesNotContain("caseInsensitive").doesNotContain("\"match\"");
    }
}
