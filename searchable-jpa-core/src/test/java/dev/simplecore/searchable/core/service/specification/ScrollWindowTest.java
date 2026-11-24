package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import java.util.List;
import java.util.Map;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.criteria.JpaOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The orders a scroll writes into its statement. The place of a missing value is part of the
 * statement, so the rows come in one order on a database that sorts missing values first and on
 * one that sorts them last.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class ScrollWindowTest {

    private static final Sort SORT = Sort.by(Sort.Order.asc("status").nullsFirst(),
            Sort.Order.desc("viewCount").nullsLast(), Sort.Order.asc("postId"));

    @Autowired
    private EntityManager em;

    private List<Order> orders(KeysetScrollPosition position) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        return new ScrollWindow(SORT, position, cb.createQuery(TestPost.class).from(TestPost.class), cb).orders();
    }

    private static List<NullPrecedence> precedences(List<Order> orders) {
        return orders.stream().map(order -> ((JpaOrder) order).getNullPrecedence()).toList();
    }

    @Test
    @DisplayName("an order that places missing values is written with that null precedence")
    void missingValuesArePlacedByTheStatement() {
        List<Order> orders = orders(null);

        assertThat(orders).extracting(Order::isAscending).containsExactly(true, false, true);
        assertThat(precedences(orders)).containsExactly(NullPrecedence.FIRST, NullPrecedence.LAST, NullPrecedence.NONE);
    }

    @Test
    @DisplayName("a backward position reverses the direction and the place of missing values")
    void aBackwardPositionReversesTheOrders() {
        List<Order> orders = orders(ScrollPosition.backward(
                Map.of("status", TestPostStatus.PUBLISHED, "viewCount", 1L, "postId", 1L)));

        assertThat(orders).extracting(Order::isAscending).containsExactly(false, true, false);
        assertThat(precedences(orders)).containsExactly(NullPrecedence.LAST, NullPrecedence.FIRST, NullPrecedence.NONE);
    }
}
