package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.SearchCondition.Group;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.NonNull;

/**
 * Builds the predicate of one node of a condition tree: a condition through the
 * {@link PredicateBuilder}, a group by combining its nodes in order.
 */
public class SpecificationBuilder<T> {
    private final PredicateBuilder<T> predicateBuilder;

    public SpecificationBuilder(@NonNull PredicateBuilder<T> predicateBuilder) {
        this.predicateBuilder = predicateBuilder;
    }

    /**
     * Creates the predicate of a node.
     * - For Group: combines its nodes in order, each joined by its own AND/OR
     * - For Condition: a single predicate from the PredicateBuilder
     *
     * @throws SearchableOperationException for a group without nodes or a node of an unknown kind
     */
    public Predicate build(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb, Node node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Group) {
            Group group = (Group) node;
            if (group.getNodes() == null || group.getNodes().isEmpty()) {
                throw new SearchableOperationException(MessageUtils.getMessage("operation.group.empty"));
            }
            return ConditionTree.fold(group.getNodes(), cb, child -> build(root, query, cb, child));
        }
        if (node instanceof Condition) {
            return predicateBuilder.build(node);
        }
        throw new SearchableOperationException(
                MessageUtils.getMessage("operation.node.unknown", new Object[]{node.getClass().getSimpleName()}));
    }
}
