package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.*;
import dev.simplecore.searchable.core.condition.SearchConditionTest.TestDTO;

/**
 * Extending an existing search condition with {@code SearchConditionBuilder.from()}.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
@DisplayName("SearchConditionBuilder.from() Tests")
class SearchConditionBuilderFromTest {

    @Test
    @DisplayName("from() creates builder with existing conditions")
    void fromExistingConditionTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .page(0)
                .size(10)
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .and(a -> a.equals("name", "test"))
                .build();

        // then
        assertThat(extended.getNodes()).hasSize(2);
        assertThat(extended.getPage()).isEqualTo(0);
        assertThat(extended.getSize()).isEqualTo(10);

        SearchCondition.Condition idCondition = (SearchCondition.Condition) extended.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");

        SearchCondition.Condition nameCondition = (SearchCondition.Condition) extended.getNodes().get(1);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getOperator()).isEqualTo(LogicalOperator.AND);
    }

    @Test
    @DisplayName("from() preserves original SearchCondition immutability")
    void fromPreservesImmutabilityTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .page(0)
                .size(10)
                .build();
        int originalNodeCount = original.getNodes().size();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .and(a -> a.equals("name", "test"))
                .and(a -> a.greaterThan("age", 20))
                .build();

        // then - original unchanged
        assertThat(original.getNodes()).hasSize(originalNodeCount);
        assertThat(original.getNodes()).hasSize(1);

        // then - extended has additional conditions
        assertThat(extended.getNodes()).hasSize(3);
    }

    @Test
    @DisplayName("from() preserves sort criteria")
    void fromPreservesSortCriteriaTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .sort(s -> s.asc("name").desc("age"))
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .and(a -> a.greaterThan("age", 20))
                .build();

        // then
        assertThat(extended.getSort()).isNotNull();
        assertThat(extended.getSort().getOrders()).hasSize(2);
        assertThat(extended.getSort().getOrders().get(0).getField()).isEqualTo("name");
        assertThat(extended.getSort().getOrders().get(0).getDirection()).isEqualTo(SearchCondition.Direction.ASC);
        assertThat(extended.getSort().getOrders().get(1).getField()).isEqualTo("age");
        assertThat(extended.getSort().getOrders().get(1).getDirection()).isEqualTo(SearchCondition.Direction.DESC);
    }

    @Test
    @DisplayName("from() allows overriding sort criteria")
    void fromAllowsOverridingSortCriteriaTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .sort(s -> s.asc("name"))
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .sort(s -> s.desc("age"))
                .build();

        // then - original sort unchanged
        assertThat(original.getSort().getOrders()).hasSize(1);
        assertThat(original.getSort().getOrders().get(0).getField()).isEqualTo("name");

        // then - new sort applied
        assertThat(extended.getSort().getOrders()).hasSize(1);
        assertThat(extended.getSort().getOrders().get(0).getField()).isEqualTo("age");
        assertThat(extended.getSort().getOrders().get(0).getDirection()).isEqualTo(SearchCondition.Direction.DESC);
    }

    @Test
    @DisplayName("from() allows overriding pagination")
    void fromAllowsOverridingPaginationTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .page(0)
                .size(10)
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .page(5)
                .size(20)
                .build();

        // then - original pagination unchanged
        assertThat(original.getPage()).isEqualTo(0);
        assertThat(original.getSize()).isEqualTo(10);

        // then - new pagination applied
        assertThat(extended.getPage()).isEqualTo(5);
        assertThat(extended.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("from() handles nested groups correctly")
    void fromWithNestedGroupsTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .and(a -> a
                                .equals("name", "test")
                                .orGreaterThan("age", 10)))
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .or(o -> o.equals("status", "ACTIVE"))
                .build();

        // then
        assertThat(extended.getNodes()).hasSize(3);  // id, AND group, OR condition

        // Validate original structure preserved
        assertThat(extended.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
        assertThat(extended.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);

        // Validate new OR condition added
        SearchCondition.Condition statusCondition = (SearchCondition.Condition) extended.getNodes().get(2);
        assertThat(statusCondition.getField()).isEqualTo("status");
        assertThat(statusCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("from() with null existing condition throws exception")
    void fromNullConditionThrowsExceptionTest() {
        assertThatThrownBy(() -> SearchConditionBuilder.from(null, TestDTO.class))
                .isInstanceOf(SearchableValidationException.class);
    }

    @Test
    @DisplayName("from() with null dtoClass throws exception")
    void fromNullDtoClassThrowsExceptionTest() {
        // given
        SearchCondition<TestDTO> existing = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .build();

        // when/then
        assertThatThrownBy(() -> SearchConditionBuilder.from(existing, null))
                .isInstanceOf(SearchableValidationException.class);
    }

    @Test
    @DisplayName("from() validates new conditions against DTO class")
    void fromValidatesNewConditionsTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .build();

        // when/then - adding invalid field should throw
        assertThatThrownBy(() ->
                SearchConditionBuilder.from(original, TestDTO.class)
                        .and(a -> a.equals("invalidField", "value"))
                        .build())
                .isInstanceOf(SearchableValidationException.class);
    }

    @Test
    @DisplayName("from() with empty original condition works correctly")
    void fromEmptyConditionTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .build();

        // then
        assertThat(extended.getNodes()).hasSize(1);
        SearchCondition.Condition idCondition = (SearchCondition.Condition) extended.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
    }

    @Test
    @DisplayName("from() preserves fetchFields from original condition")
    void fromPreservesFetchFieldsTest() {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .fetchFields("author", "category")
                .build();

        // when
        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .and(a -> a.equals("name", "test"))
                .build();

        // then
        assertThat(extended.getFetchFields()).containsExactlyInAnyOrder("author", "category");
    }
}
