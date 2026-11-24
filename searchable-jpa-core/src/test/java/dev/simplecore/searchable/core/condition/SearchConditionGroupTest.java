package dev.simplecore.searchable.core.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import dev.simplecore.searchable.core.condition.SearchConditionTest.TestDTO;

/**
 * How nested AND and OR groups are assembled by the builder.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
@DisplayName("Nested AND/OR Group Tests")
class SearchConditionGroupTest {

    @Test
    @DisplayName("and() with condition and nested or() preserves both conditions")
    void andWithConditionAndNestedOrTest() {
        // given - pattern: builder.and(a -> a.lessThanOrEqualTo(...).or(b -> b.isNull(...)))
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .or(b -> b.greaterThan("age", 50)))
                .build();

        // when
        List<SearchCondition.Node> nodes = condition.getNodes();

        // then - should have 2 nodes: id condition and AND group
        assertThat(nodes).hasSize(2);

        // Validate id condition
        assertThat(nodes.get(0)).isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition idCondition = (SearchCondition.Condition) nodes.get(0);
        assertThat(idCondition.getField()).isEqualTo("id");

        // Validate AND group contains both lessThan condition AND OR group
        assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
        assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(andGroup.getNodes()).hasSize(2);  // lessThan + OR group

        // First node in group should be the lessThan condition
        assertThat(andGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition lessThanCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
        assertThat(lessThanCondition.getField()).isEqualTo("age");
        assertThat(lessThanCondition.getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);

        // Second node in group should be the OR group
        assertThat(andGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group orGroup = (SearchCondition.Group) andGroup.getNodes().get(1);
        assertThat(orGroup.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("or() with condition and nested and() preserves both conditions")
    void orWithConditionAndNestedAndTest() {
        // given - pattern: builder.or(a -> a.equals(...).and(b -> b.greaterThan(...)))
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .or(a -> a
                        .equals("status", "ACTIVE")
                        .and(b -> b.greaterThan("age", 20)))
                .build();

        // when
        List<SearchCondition.Node> nodes = condition.getNodes();

        // then - should have 2 nodes: id condition and OR group
        assertThat(nodes).hasSize(2);

        // Validate OR group contains both equals condition AND AND group
        assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group orGroup = (SearchCondition.Group) nodes.get(1);
        assertThat(orGroup.getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(orGroup.getNodes()).hasSize(2);  // equals + AND group

        // First node in group should be the equals condition
        assertThat(orGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition equalsCondition = (SearchCondition.Condition) orGroup.getNodes().get(0);
        assertThat(equalsCondition.getField()).isEqualTo("status");
        assertThat(equalsCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
    }

    @Test
    @DisplayName("Multiple conditions with nested or() in and() preserves all conditions")
    void multipleConditionsWithNestedOrTest() {
        // given - pattern mimicking the user's publish/expire scenario
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .or(b -> b.greaterThan("age", 50)))
                .and(a -> a
                        .greaterThan("score", 3.0)
                        .or(b -> b.equals("score", 0.0)))
                .build();

        // when
        List<SearchCondition.Node> nodes = condition.getNodes();

        // then - should have 3 nodes: id condition, first AND group, second AND group
        assertThat(nodes).hasSize(3);

        // Both AND groups should contain their respective conditions and OR groups
        SearchCondition.Group firstAndGroup = (SearchCondition.Group) nodes.get(1);
        assertThat(firstAndGroup.getNodes()).hasSize(2);
        assertThat(firstAndGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
        assertThat(firstAndGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);

        SearchCondition.Group secondAndGroup = (SearchCondition.Group) nodes.get(2);
        assertThat(secondAndGroup.getNodes()).hasSize(2);
        assertThat(secondAndGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
        assertThat(secondAndGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);
    }

    @Test
    @DisplayName("JSON serialization preserves nested and/or structure")
    void jsonSerializationWithNestedGroupsTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .or(b -> b.greaterThan("age", 50)))
                .build();

        // when
        String json = original.toJson();
        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(deserialized.getNodes()).hasSize(2);
        assertThat(deserialized.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);

        SearchCondition.Group andGroup = (SearchCondition.Group) deserialized.getNodes().get(1);
        assertThat(andGroup.getNodes()).hasSize(2);
    }

    @Test
    @DisplayName("and() with orEquals() preserves both conditions")
    void andWithOrEqualsTest() {
        // given - pattern: builder.and(a -> a.lessThan(...).orEquals(...))
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .orEquals("age", 100))
                .build();

        // when
        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();

        assertThat(nodes).hasSize(2);

        SearchCondition.Condition idCondition = (SearchCondition.Condition) condition.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");

        SearchCondition.Condition lessThanCondition = (SearchCondition.Condition) nodes.get(0);
        assertThat(lessThanCondition.getField()).isEqualTo("age");
        assertThat(lessThanCondition.getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);
        assertThat(lessThanCondition.getOperator()).isNull();

        SearchCondition.Condition orEqualsCondition = (SearchCondition.Condition) nodes.get(1);
        assertThat(orEqualsCondition.getField()).isEqualTo("age");
        assertThat(orEqualsCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(orEqualsCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("and() with orGreaterThan() preserves both conditions")
    void andWithOrGreaterThanTest() {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .orGreaterThan("age", 50))
                .build();

        // when
        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();

        // then
        assertThat(nodes).hasSize(2);

        SearchCondition.Condition orGreaterThanCondition = (SearchCondition.Condition) nodes.get(1);
        assertThat(orGreaterThanCondition.getField()).isEqualTo("age");
        assertThat(orGreaterThanCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
        assertThat(orGreaterThanCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("and() with multiple orXXX() methods preserves all conditions")
    void andWithMultipleOrMethodsTest() {
        // given - pattern: builder.and(a -> a.lessThan(...).orEquals(...).orGreaterThan(...))
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .orEquals("age", 50)
                        .orGreaterThan("age", 100))
                .build();

        // when
        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();

        // then - all conditions should be present
        assertThat(nodes).hasSize(3);

        // Validate all conditions
        assertThat(((SearchCondition.Condition) condition.getNodes().get(0)).getField()).isEqualTo("id");
        assertThat(((SearchCondition.Condition) nodes.get(0)).getField()).isEqualTo("age");
        assertThat(((SearchCondition.Condition) nodes.get(0)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
        assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("and() with orXXX() combined with nested or() preserves all conditions")
    void andWithOrMethodsAndNestedOrTest() {
        // given - mixed pattern: orEquals() + nested or()
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 30)
                        .orEquals("age", 50)
                        .or(b -> b.greaterThan("age", 100)))
                .build();

        // when
        List<SearchCondition.Node> nodes = condition.getNodes();

        // then - should have id condition + AND group (which contains conditions and nested OR group)
        assertThat(nodes).hasSize(2);

        // Validate AND group contains all conditions including orEquals and nested or()
        assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
        assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(andGroup.getNodes()).hasSize(3);  // lessThan + orEquals + OR group

        // First: lessThan condition
        assertThat(andGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition lessThanCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
        assertThat(lessThanCondition.getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);

        // Second: orEquals condition
        assertThat(andGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition orEqualsCondition = (SearchCondition.Condition) andGroup.getNodes().get(1);
        assertThat(orEqualsCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(orEqualsCondition.getOperator()).isEqualTo(LogicalOperator.OR);

        // Third: nested OR group
        assertThat(andGroup.getNodes().get(2)).isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group orGroup = (SearchCondition.Group) andGroup.getNodes().get(2);
        assertThat(orGroup.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Simulating publish/expire scenario with orIsNull()")
    void publishExpireScenarioWithOrIsNullTest() {
        // given - simulating: (publishAt <= now OR publishAt IS NULL) AND (expireAt > now OR expireAt IS NULL)
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThanOrEqualTo("age", 30)
                        .orIsNull("age"))
                .and(a -> a
                        .greaterThan("score", 3.0)
                        .orIsNull("score"))
                .build();

        // when
        assertThat(condition.getNodes()).hasSize(3);
        for (int i = 1; i <= 2; i++) {
            SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(i);
            assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
            assertThat(group.getNodes()).hasSize(2);
            assertThat(group.getNodes().get(1).getOperator()).isEqualTo(LogicalOperator.OR);
        }
    }

    // ========== Additional orXXX() Method Tests ==========
}
