package dev.simplecore.searchable.core.condition.validator;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;

/** Resource limits checked independently of DTO validation, including server-built conditions. */
public final class SearchRequestLimits {
    public static final int MAX_NODES = 1000;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_IN_VALUES = 1000;
    public static final int MAX_SORT_FIELDS = 16;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_PAGE_SIZE = 1000;

    /**
     * How large one search request may be.
     *
     * @param maxNodes the most conditions and groups a request may hold
     * @param maxDepth the deepest a group may be nested
     * @param maxInValues the most values one condition may list
     * @param maxSortFields the most fields a request may sort by
     */
    public record Structure(int maxNodes, int maxDepth, int maxInValues, int maxSortFields) {

        public Structure {
            requirePositive("maxNodes", maxNodes);
            requirePositive("maxDepth", maxDepth);
            requirePositive("maxInValues", maxInValues);
            requirePositive("maxSortFields", maxSortFields);
        }

        private static void requirePositive(String name, int value) {
            if (value < 1) {
                throw new SearchableConfigurationException(MessageUtils.getMessage(
                        "settings.positive.integer.required", new Object[]{name, String.valueOf(value)}));
            }
        }
    }

    // A request is measured while it is still being read, before any persistence unit is known, so
    // these limits belong to the process.
    private static volatile Structure structure = new Structure(MAX_NODES, MAX_DEPTH, MAX_IN_VALUES, MAX_SORT_FIELDS);

    private SearchRequestLimits() { }

    /**
     * Replaces the limits on the size of a request. Called once during application startup.
     *
     * @param limits the limits every request is measured against from now on
     */
    public static void setStructureLimits(Structure limits) {
        structure = limits;
    }

    /** @return the limits on the size of a request */
    public static Structure structureLimits() {
        return structure;
    }

    public static void validateStructure(SearchCondition<?> condition) {
        Structure limits = structure;
        visit(condition.getNodes(), 0, new int[1], limits);
        if (condition.getSort() != null && condition.getSort().getOrders().size() > limits.maxSortFields()) {
            fail("limits.sort.fields", limits.maxSortFields());
        }
    }

    private static void visit(List<SearchCondition.Node> nodes, int depth, int[] count, Structure limits) {
        if (depth > limits.maxDepth()) {
            fail("limits.depth", limits.maxDepth());
        }
        if (nodes == null) {
            throw new SearchableValidationException(MessageUtils.getMessage("limits.nodes.null"));
        }
        for (SearchCondition.Node node : nodes) {
            if (++count[0] > limits.maxNodes()) {
                fail("limits.nodes", limits.maxNodes());
            }
            if (node instanceof SearchCondition.Group group) {
                visit(group.getNodes(), depth + 1, count, limits);
            } else if (node instanceof SearchCondition.Condition leaf) {
                if (leaf.getValue() instanceof Collection<?> values && values.size() > limits.maxInValues()) {
                    fail("limits.collection", limits.maxInValues());
                }
            } else {
                throw new SearchableValidationException(MessageUtils.getMessage("limits.node.type"));
            }
        }
    }

    public static int defaultPageSize(EntityManager em) {
        return SearchableJpaSettings.positiveInt(em, SearchableJpaSettings.DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE);
    }

    public static void validatePage(EntityManager em, int page, int size) {
        int maximum = SearchableJpaSettings.positiveInt(em, SearchableJpaSettings.MAX_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
        if (page < 0 || size < 1 || size > maximum) {
            fail("limits.page.range", maximum);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new SearchableValidationException(MessageUtils.getMessage("limits.page.offset"));
        }
    }

    /** The limit is written as plain digits: a message format would group them by the locale. */
    private static void fail(String key, int limit) {
        throw new SearchableValidationException(MessageUtils.getMessage(key, new Object[]{String.valueOf(limit)}));
    }
}
