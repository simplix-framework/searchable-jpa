package dev.simplecore.searchable.core.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for SearchCondition serialization and deserialization.
 * Verifies the proper handling of conditions, groups, and various data types.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchConditionTest {

    @Test
    @DisplayName("Test serialization/deserialization of simple conditions")
    void singleConditionSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .greaterThan("age", 20)
                        .and(a -> a
                                .equals("name", "test")
                                .orGreaterThan("age", 10))
                )
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);

        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);  // Three nodes: id condition, age condition, and one group

        // Validate id condition
        assertThat(deserialized.getNodes().get(0))
                .isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition idCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
        assertThat(String.valueOf(idCondition.getValue())).isEqualTo("1");
        assertThat(idCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age condition
        assertThat(deserialized.getNodes().get(1))
                .isInstanceOf(SearchCondition.Condition.class)
                .extracting(node -> String.valueOf(((SearchCondition.Condition)node).getValue()))
                .isEqualTo("20");
        SearchCondition.Condition ageCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(ageCondition.getField()).isEqualTo("age");
        assertThat(ageCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);

        // Validate AND group
        assertThat(deserialized.getNodes().get(2))
                .isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group andGroup = (SearchCondition.Group) deserialized.getNodes().get(2);
        assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(andGroup.getNodes()).hasSize(2);

        // Validate name condition
        SearchCondition.Condition nameCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getValue()).isEqualTo("test");
        assertThat(nameCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age OR condition
        SearchCondition.Condition ageOrCondition = (SearchCondition.Condition) andGroup.getNodes().get(1);
        assertThat(ageOrCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageOrCondition.getValue())).isEqualTo("10");
        assertThat(ageOrCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
        assertThat(ageOrCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Test serialization/deserialization of AND conditions")
    void andConditionsSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.equals("name", "test"))
                .and(a -> a.greaterThan("age", 20))
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);
        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);

        // Validate id condition
        SearchCondition.Condition idCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
        assertThat(String.valueOf(idCondition.getValue())).isEqualTo("1");
        assertThat(idCondition.getOperator()).isNull();  // First condition has null operator

        // Validate name condition
        SearchCondition.Condition nameCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getValue()).isEqualTo("test");
        assertThat(nameCondition.getOperator()).isEqualTo(LogicalOperator.AND);

        // Validate age condition
        SearchCondition.Condition ageCondition = (SearchCondition.Condition) deserialized.getNodes().get(2);
        assertThat(ageCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageCondition.getValue())).isEqualTo("20");
        assertThat(ageCondition.getOperator()).isEqualTo(LogicalOperator.AND);
    }

    @Test
    @DisplayName("Test serialization/deserialization of OR conditions")
    void orConditionsSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("status", "ACTIVE"))
                .or(o -> o.equals("status", "PENDING"))
                .or(o -> o.equals("status", "PROCESSING"))
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);
        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);

        // Validate first status condition
        SearchCondition.Condition firstCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(firstCondition.getField()).isEqualTo("status");
        assertThat(firstCondition.getValue()).isEqualTo("ACTIVE");
        assertThat(firstCondition.getOperator()).isNull();  // First condition has null operator

        // Validate second status condition
        SearchCondition.Condition secondCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(secondCondition.getField()).isEqualTo("status");
        assertThat(secondCondition.getValue()).isEqualTo("PENDING");
        assertThat(secondCondition.getOperator()).isEqualTo(LogicalOperator.OR);

        // Validate third status condition
        SearchCondition.Condition thirdCondition = (SearchCondition.Condition) deserialized.getNodes().get(2);
        assertThat(thirdCondition.getField()).isEqualTo("status");
        assertThat(thirdCondition.getValue()).isEqualTo("PROCESSING");
        assertThat(thirdCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Test serialization/deserialization of nested conditions")
    void nestedConditionsSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .greaterThan("age", 11)
                        .and(a -> a
                                .equals("name", "test")
                                .orGreaterThan("age", 12))
                )
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);

        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":[{\"conditions\":");  // Should not have deeply nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);  // Three nodes: id condition, age condition, and one group

        // Validate id condition
        SearchCondition.Condition idCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
        assertThat(String.valueOf(idCondition.getValue())).isEqualTo("1");
        assertThat(idCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age condition
        SearchCondition.Condition ageCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(ageCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageCondition.getValue())).isEqualTo("11");
        assertThat(ageCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);

        // Validate AND group
        assertThat(deserialized.getNodes().get(2))
                .isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group andGroup = (SearchCondition.Group) deserialized.getNodes().get(2);
        assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(andGroup.getNodes()).hasSize(2);

        // Validate name condition
        SearchCondition.Condition nameCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getValue()).isEqualTo("test");
        assertThat(nameCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age OR condition
        SearchCondition.Condition ageOrCondition = (SearchCondition.Condition) andGroup.getNodes().get(1);
        assertThat(ageOrCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageOrCondition.getValue())).isEqualTo("12");
        assertThat(ageOrCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
        assertThat(ageOrCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    // @Test
    // @DisplayName("Test serialization/deserialization with different value types")
    // void differentTypesSerializationTest() throws JsonProcessingException {
    //     // given
    //     SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
    //         .where(w -> w
    //             .equals("id", 1L)
    //             .equals("name", "test")
    //             .equals("age", 20)
    //             .equals("active", true)
    //             .equals("status", "ACTIVE")
    //             .equals("createdAt", LocalDateTime.now())
    //             .equals("score", 4.5))
    //         .page(0)
    //         .size(10)
    //         .build();

    //     // when
    //     String json = condition.toJson();
    //     SearchCondition<TestDTO> deserialized = condition.fromJson(json, TestDTO.class);

    //     // then
    //     assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
    //     assertThat(deserialized.getNodes()).hasSize(7);
    //     assertThat(deserialized.getNodes().stream()
    //             .allMatch(node -> node instanceof SearchCondition.Condition))
    //         .isTrue();
    // }

    // @Test
    // @DisplayName("Test serialization/deserialization with @JsonIgnore fields")
    // void jsonIgnoreFieldsTest() throws JsonProcessingException {
    //     // given
    //     SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
    //         .where(w -> w.equals("id", 1))
    //         .page(0)
    //         .size(10)
    //         .build();

    //     // when
    //     String json = condition.toJson();
    //     SearchCondition<TestDTO> deserialized = condition.fromJson(json, TestDTO.class);

    //     // then
    //     assertThat(json).doesNotContain("dtoClass");  // @JsonIgnore field should not be serialized
    //     assertThat(json).doesNotContain("entityField");  // @JsonIgnore field should not be serialized
    //     assertThat(deserialized.getNodes()).hasSize(1);
    //     assertThat(deserialized.getNodes().get(0))
    //         .isInstanceOf(SearchCondition.Condition.class);
    // }

    @Nested
    @DisplayName("FetchFields Tests")
    class FetchFieldsTests {

        @Test
        @DisplayName("fetchFields with varargs sets fields correctly")
        void fetchFieldsVarargsTest() {
            // given & when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields("author", "author.profile", "category")
                    .build();

            // then
            assertThat(condition.getFetchFields())
                    .containsExactlyInAnyOrder("author", "author.profile", "category");
        }

        @Test
        @DisplayName("fetchFields with Set sets fields correctly")
        void fetchFieldsSetTest() {
            // given
            Set<String> fields = new HashSet<>(Arrays.asList("author", "category"));

            // when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields(fields)
                    .build();

            // then
            assertThat(condition.getFetchFields())
                    .containsExactlyInAnyOrder("author", "category");
        }

        @Test
        @DisplayName("fetchFields is ignored during JSON deserialization")
        void fetchFieldsJsonIgnoreTest() throws JsonProcessingException {
            // given - JSON with fetchFields (simulating malicious client input)
            String jsonWithFetchFields = """
                    {
                      "conditions": [
                        {
                          "field": "id",
                          "searchOperator": "EQUALS",
                          "value": 1
                        }
                      ],
                      "fetchFields": ["author", "category"],
                      "page": 0,
                      "size": 10
                    }
                    """;

            // when
            SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(jsonWithFetchFields, TestDTO.class);

            // then - fetchFields should be empty (ignored during deserialization)
            assertThat(deserialized.getFetchFields()).isEmpty();
        }

        @Test
        @DisplayName("fetchFields is not included in JSON serialization")
        void fetchFieldsNotSerializedTest() throws JsonProcessingException {
            // given
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields("author", "category")
                    .build();

            // when
            String json = condition.toJson();

            // then - fetchFields should not appear in JSON
            assertThat(json).doesNotContain("fetchFields");
            assertThat(json).doesNotContain("author");
            assertThat(json).doesNotContain("category");
        }

        @Test
        @DisplayName("fetchFields can be set after other builder methods")
        void fetchFieldsMethodChainingTest() {
            // given & when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .sort(s -> s.asc("name"))
                    .page(0)
                    .size(10)
                    .fetchFields("author")
                    .build();

            // then
            assertThat(condition.getFetchFields()).containsExactly("author");
            assertThat(condition.getSort()).isNotNull();
            assertThat(condition.getPage()).isEqualTo(0);
            assertThat(condition.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("empty fetchFields returns empty set")
        void emptyFetchFieldsTest() {
            // given & when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .build();

            // then
            assertThat(condition.getFetchFields()).isNotNull();
            assertThat(condition.getFetchFields()).isEmpty();
        }
    }

    /**
     * Test DTO class with various field types for testing search conditions
     */
    @Data
    public static class TestDTO {
        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private Long id;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.CONTAINS,
                SearchOperator.NOT_CONTAINS,
                SearchOperator.STARTS_WITH,
                SearchOperator.NOT_STARTS_WITH,
                SearchOperator.ENDS_WITH,
                SearchOperator.NOT_ENDS_WITH,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        }, sortable = true)
        private String name;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.GREATER_THAN_OR_EQUAL_TO,
                SearchOperator.LESS_THAN,
                SearchOperator.LESS_THAN_OR_EQUAL_TO,
                SearchOperator.BETWEEN,
                SearchOperator.NOT_BETWEEN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        }, sortable = true)
        private Integer age;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private Boolean active;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.IN,
                SearchOperator.NOT_IN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private String status;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.GREATER_THAN_OR_EQUAL_TO,
                SearchOperator.LESS_THAN,
                SearchOperator.LESS_THAN_OR_EQUAL_TO,
                SearchOperator.BETWEEN,
                SearchOperator.NOT_BETWEEN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private LocalDateTime createdAt;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.GREATER_THAN_OR_EQUAL_TO,
                SearchOperator.LESS_THAN,
                SearchOperator.LESS_THAN_OR_EQUAL_TO,
                SearchOperator.BETWEEN,
                SearchOperator.NOT_BETWEEN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private Double score;
    }
} 