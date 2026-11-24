package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.function.Function;

/**
 * Combines the nodes of one level of a condition tree.
 */
final class ConditionTree {

    private ConditionTree() {
    }

    /**
     * Folds the nodes left to right, each joined to what precedes it by its own operator; a node
     * without one is joined with AND. The first node's operator has nothing to join and is not read.
     * A node that yields no predicate restricts nothing and is left out.
     *
     * @param nodes the nodes of one level
     * @param cb the criteria builder
     * @param predicateOf how one node becomes a predicate
     * @return the combined predicate, or null when no node yields one
     */
    static Predicate fold(List<Node> nodes, CriteriaBuilder cb, Function<Node, Predicate> predicateOf) {
        Predicate result = null;
        for (Node node : nodes) {
            Predicate current = predicateOf.apply(node);
            if (current == null) {
                continue;
            }
            if (result == null) {
                result = current;
            } else if (node.getOperator() == LogicalOperator.OR) {
                result = cb.or(result, current);
            } else {
                result = cb.and(result, current);
            }
        }
        return result;
    }
}
