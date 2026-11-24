package dev.simplecore.searchable.core.service.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

/**
 * The ordering and the keyset condition of one window of a scroll.
 *
 * <p>An order that places missing values first or last is written with that null precedence, and
 * the keyset condition places a missing value where the order places it. A backward position
 * reads the rows in the reverse order of the sort.
 */
final class ScrollWindow {

    private final Sort sort;
    private final KeysetScrollPosition position;
    private final CriteriaBuilder cb;
    private final List<Expression<?>> values = new ArrayList<>();

    /**
     * @param sort the orders of the search followed by the parts of the primary key
     * @param position the keyset position of the window, or null when the window is cut by offset
     * @param root the entity the statement selects
     * @param cb the builder of the statement
     */
    ScrollWindow(Sort sort, KeysetScrollPosition position, Root<?> root, CriteriaBuilder cb) {
        this.sort = sort;
        this.position = position;
        this.cb = cb;
        for (Sort.Order order : sort) {
            values.add(QueryUtils.toOrders(Sort.by(order.nullsNative()), root, cb).get(0).getExpression());
        }
    }

    /** The orders of the statement: those of the sort, reversed for a backward position. */
    List<Order> orders() {
        HibernateCriteriaBuilder builder = (HibernateCriteriaBuilder) cb;
        List<Order> orders = new ArrayList<>();
        int index = 0;
        for (Sort.Order order : sort) {
            Expression<?> value = values.get(index++);
            boolean ascending = ascending(order);
            Boolean missingFirst = missingFirst(order);
            if (missingFirst == null) {
                orders.add(ascending ? cb.asc(value) : cb.desc(value));
            } else {
                orders.add(ascending ? builder.asc(value, missingFirst) : builder.desc(value, missingFirst));
            }
        }
        return orders;
    }

    /**
     * The rows that follow the position in the direction it scrolls.
     *
     * @return the condition, or null for a position that has no keys yet
     */
    Predicate afterPosition() {
        if (position == null || position.isInitial()) {
            return null;
        }
        Map<String, Object> keys = position.getKeys();
        List<Predicate> alternatives = new ArrayList<>();
        List<Predicate> tied = new ArrayList<>();
        int index = 0;
        for (Sort.Order order : sort) {
            Expression<?> value = values.get(index++);
            Object key = keys.get(order.getProperty());
            Predicate beyond = beyond(order, value, key);
            if (beyond != null) {
                List<Predicate> alternative = new ArrayList<>(tied);
                alternative.add(beyond);
                alternatives.add(cb.and(alternative.toArray(Predicate[]::new)));
            }
            tied.add(key == null ? cb.isNull(value) : cb.equal(value, key));
        }
        return cb.or(alternatives.toArray(Predicate[]::new));
    }

    /**
     * Cuts the rows read for a keyset position into the window.
     *
     * @param rows the rows the statement returned, one more than the size when more rows follow
     * @param size the size of a window
     * @param position the position the rows were read from
     * @param sort the sort of the statement
     * @param entity the entity the rows are instances of
     * @return the rows in the order of the sort, each with the position that continues after it
     */
    static <T> Window<T> cut(List<T> rows, int size, KeysetScrollPosition position, Sort sort,
                             JpaEntityInformation<T, ?> entity) {
        boolean more = rows.size() > size;
        List<T> window = new ArrayList<>(more ? rows.subList(0, size) : rows);
        if (position.scrollsBackward()) {
            Collections.reverse(window);
        }
        List<String> properties = sort.stream().map(Sort.Order::getProperty).toList();
        return Window.from(window,
                index -> ScrollPosition.of(entity.getKeyset(properties, window.get(index)), position.getDirection()),
                more);
    }

    private boolean backward() {
        return position != null && position.scrollsBackward();
    }

    private boolean ascending(Sort.Order order) {
        return order.isAscending() != backward();
    }

    /** Whether missing values come first in the statement; null when the order leaves it to the database. */
    private Boolean missingFirst(Sort.Order order) {
        if (order.getNullHandling() == Sort.NullHandling.NATIVE) {
            return null;
        }
        return (order.getNullHandling() == Sort.NullHandling.NULLS_FIRST) != backward();
    }

    /** The rows whose value of this order comes after the key; null when no value does. */
    @SuppressWarnings("unchecked")
    private Predicate beyond(Sort.Order order, Expression<?> value, Object key) {
        Boolean missingFirst = missingFirst(order);
        if (key == null) {
            return Boolean.TRUE.equals(missingFirst) ? cb.isNotNull(value) : null;
        }
        Expression<Comparable<Object>> comparable = (Expression<Comparable<Object>>) value;
        Comparable<Object> bound = (Comparable<Object>) key;
        Predicate past = ascending(order) ? cb.greaterThan(comparable, bound) : cb.lessThan(comparable, bound);
        return Boolean.FALSE.equals(missingFirst) ? cb.or(past, cb.isNull(value)) : past;
    }
}
