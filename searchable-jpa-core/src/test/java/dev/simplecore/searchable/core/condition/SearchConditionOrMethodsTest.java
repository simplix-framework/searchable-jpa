package dev.simplecore.searchable.core.condition;

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

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import dev.simplecore.searchable.core.condition.SearchConditionTest.TestDTO;

/**
 * The or-prefixed builder methods and their combination with nested groups.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
@DisplayName("OR Method Tests")
class SearchConditionOrMethodsTest {
    @Test
    @DisplayName("orNotEquals() preserves condition with OR operator")
    void orNotEqualsTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.equals("status", "ACTIVE").orNotEquals("status", "DELETED"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.NOT_EQUALS);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orGreaterThanOrEqualTo() preserves condition with OR operator")
    void orGreaterThanOrEqualToTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.lessThan("age", 20).orGreaterThanOrEqualTo("age", 50))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN_OR_EQUAL_TO);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orLessThan() preserves condition with OR operator")
    void orLessThanTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.greaterThan("age", 50).orLessThan("age", 20))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orLessThanOrEqualTo() preserves condition with OR operator")
    void orLessThanOrEqualToTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.greaterThan("age", 50).orLessThanOrEqualTo("age", 20))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN_OR_EQUAL_TO);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orContains() preserves condition with OR operator")
    void orContainsTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.startsWith("name", "John").orContains("name", "Smith"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.CONTAINS);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orNotContains() preserves condition with OR operator")
    void orNotContainsTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.contains("name", "John").orNotContains("name", "Doe"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.NOT_CONTAINS);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orStartsWith() preserves condition with OR operator")
    void orStartsWithTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.endsWith("name", "son").orStartsWith("name", "John"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.STARTS_WITH);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orNotStartsWith() preserves condition with OR operator")
    void orNotStartsWithTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.startsWith("name", "John").orNotStartsWith("name", "Jane"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.NOT_STARTS_WITH);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orEndsWith() preserves condition with OR operator")
    void orEndsWithTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.startsWith("name", "John").orEndsWith("name", "son"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.ENDS_WITH);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orNotEndsWith() preserves condition with OR operator")
    void orNotEndsWithTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.endsWith("name", "son").orNotEndsWith("name", "ley"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.NOT_ENDS_WITH);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orIsNull() preserves condition with OR operator")
    void orIsNullTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.greaterThan("age", 20).orIsNull("age"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.IS_NULL);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orIsNotNull() preserves condition with OR operator")
    void orIsNotNullTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.isNull("age").orIsNotNull("score"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.IS_NOT_NULL);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orIn() preserves condition with OR operator")
    void orInTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.equals("status", "ACTIVE").orIn("status", Arrays.asList("PENDING", "PROCESSING")))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.IN);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orNotIn() preserves condition with OR operator")
    void orNotInTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.equals("status", "ACTIVE").orNotIn("status", Arrays.asList("DELETED", "ARCHIVED")))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.NOT_IN);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orBetween() preserves condition with OR operator")
    void orBetweenTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.lessThan("age", 20).orBetween("age", 50, 60))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.BETWEEN);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("orNotBetween() preserves condition with OR operator")
    void orNotBetweenTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.between("age", 30, 40).orNotBetween("age", 50, 60))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.NOT_BETWEEN);
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    // ========== Edge Case Tests ==========

    @Test
    @DisplayName("Complex: Multiple orXXX() methods chained together")
    void multipleOrMethodsChainedTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .equals("age", 25)
                        .orLessThan("age", 18)
                        .orGreaterThan("age", 65)
                        .orIsNull("age"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(4);

        // All orXXX() conditions should have OR operator
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(((SearchCondition.Condition) nodes.get(3)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Complex: orXXX() with nested or() in same group")
    void orMethodWithNestedOrInSameGroupTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 20)
                        .orEquals("age", 25)
                        .orGreaterThan("age", 60)
                        .or(b -> b.isNull("age")))
                .build();

        List<SearchCondition.Node> nodes = condition.getNodes();
        // Should have id condition and AND group
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);

        SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
        // Should have: lessThan, orEquals, orGreaterThan, OR group
        assertThat(andGroup.getNodes()).hasSize(4);
    }

    @Test
    @DisplayName("Complex: Multiple nested groups with orXXX()")
    void multipleNestedGroupsWithOrMethodsTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a
                        .lessThan("age", 20)
                        .orIsNull("age")
                        .or(b -> b.greaterThan("age", 60)))
                .and(a -> a
                        .equals("status", "ACTIVE")
                        .orIn("status", Arrays.asList("PENDING", "PROCESSING"))
                        .or(b -> b.isNull("status")))
                .build();

        List<SearchCondition.Node> nodes = condition.getNodes();
        // id condition + 2 AND groups
        assertThat(nodes).hasSize(3);

        SearchCondition.Group firstGroup = (SearchCondition.Group) nodes.get(1);
        SearchCondition.Group secondGroup = (SearchCondition.Group) nodes.get(2);

        // Each group has: condition + orXXX + nested OR group
        assertThat(firstGroup.getNodes()).hasSize(3);
        assertThat(secondGroup.getNodes()).hasSize(3);
    }

    @Test
    @DisplayName("Edge: Single condition with orXXX() only")
    void singleConditionWithOrOnlyTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L).orEquals("id", 2L))
                .build();

        List<SearchCondition.Node> nodes = condition.getNodes();
        assertThat(nodes).hasSize(2);
        assertThat(((SearchCondition.Condition) nodes.get(0)).getOperator()).isNull();
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Edge: Empty and() with only orXXX()")
    void emptyAndWithOrOnlyTest() {
        // This tests when first condition is followed only by orXXX
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.equals("age", 25).orEquals("age", 30).orEquals("age", 35))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
        SearchCondition.Group group = (SearchCondition.Group) condition.getNodes().get(1);
        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        List<SearchCondition.Node> nodes = group.getNodes();
        assertThat(nodes).hasSize(3); // id, age=25, age=30, age=35
    }

    @Test
    @DisplayName("Edge: Deeply nested and/or groups")
    void deeplyNestedGroupsTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .and(a -> a
                                .lessThan("age", 30)
                                .or(b -> b
                                        .greaterThan("age", 50)
                                        .and(c -> c.equals("status", "VIP")))))
                .build();

        List<SearchCondition.Node> nodes = condition.getNodes();
        assertThat(nodes).hasSize(2); // id condition + AND group

        SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
        assertThat(andGroup.getNodes()).hasSize(2); // lessThan + OR group

        SearchCondition.Group orGroup = (SearchCondition.Group) andGroup.getNodes().get(1);
        assertThat(orGroup.getNodes()).hasSize(2); // greaterThan + AND group
    }

    @Test
    @DisplayName("Edge: or() followed by and() in same builder")
    void orFollowedByAndTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .or(o -> o.equals("id", 2L))
                .and(a -> a.greaterThan("age", 20))
                .build();

        List<SearchCondition.Node> nodes = condition.getNodes();
        assertThat(nodes).hasSize(3);
        assertThat(((SearchCondition.Condition) nodes.get(0)).getOperator()).isNull();
        assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.AND);
    }

    @Test
    @DisplayName("Edge: Alternating and/or with orXXX")
    void alternatingAndOrWithOrMethodsTest() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.greaterThan("age", 20).orIsNull("age"))
                .or(o -> o.equals("status", "SPECIAL"))
                .and(a -> a.lessThan("score", 100.0).orIsNull("score"))
                .build();

        assertThat(condition.getNodes()).hasSize(4);
        assertThat(condition.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);
        assertThat(condition.getNodes().get(2).getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(condition.getNodes().get(3)).isInstanceOf(SearchCondition.Group.class);
        assertThat(condition.getNodes().get(3).getOperator()).isEqualTo(LogicalOperator.AND);
    }
}
