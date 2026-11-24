package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchRequestLimitsTest {

    @BeforeEach void readMessagesInEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach void restoreLocaleAndLimits() {
        LocaleContextHolder.resetLocaleContext();
        SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(SearchRequestLimits.MAX_NODES,
                SearchRequestLimits.MAX_DEPTH, SearchRequestLimits.MAX_IN_VALUES, SearchRequestLimits.MAX_SORT_FIELDS));
    }

    @Test void theProcessWideLimitsCanBeTightenedAndWidened() {
        SearchCondition<Object> condition = new SearchCondition<>();
        var leaf = new SearchCondition.Condition(null, "name", SearchOperator.EQUALS, "A", null, "name");
        condition.getNodes().addAll(Collections.nCopies(3, leaf));

        SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(2, 32, 1000, 16));
        assertThatThrownBy(() -> SearchRequestLimits.validateStructure(condition))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("node count 2");

        SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(5000, 32, 5000, 16));
        condition.getNodes().addAll(Collections.nCopies(2000, leaf));
        condition.getNodes().add(new SearchCondition.Condition(null, "id", SearchOperator.IN,
                Collections.nCopies(3000, 1), null, "id"));
        assertThatCode(() -> SearchRequestLimits.validateStructure(condition)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new SearchRequestLimits.Structure(0, 32, 1000, 16))
                .isInstanceOf(SearchableConfigurationException.class);
    }
    @Test void cyclicGroupsAreRejectedWithoutStackOverflow() {
        SearchCondition<Object> condition = new SearchCondition<>();
        var group = new SearchCondition.Group(LogicalOperator.AND, new ArrayList<>());
        group.getNodes().add(group);
        condition.getNodes().add(group);
        assertThatThrownBy(() -> SearchRequestLimits.validateStructure(condition))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("depth");
    }

    @Test void nodeAndCollectionLimitsApplyToServerBuiltConditions() {
        SearchCondition<Object> condition = new SearchCondition<>();
        var leaf = new SearchCondition.Condition(null, "name", SearchOperator.EQUALS, "A", null, "name");
        condition.getNodes().addAll(Collections.nCopies(1001, leaf));
        assertThatThrownBy(() -> SearchRequestLimits.validateStructure(condition)).hasMessageContaining("node count");
        condition.getNodes().clear();
        condition.getNodes().add(new SearchCondition.Condition(null, "id", SearchOperator.IN,
                Collections.nCopies(1001, 1), null, "id"));
        assertThatThrownBy(() -> SearchRequestLimits.validateStructure(condition)).hasMessageContaining("collection");
    }

    @Test void pageSettingsAreIsolatedPerPersistenceUnit() {
        EntityManager first = manager(Map.of("searchable.default_page_size", "50", "searchable.max_page_size", "2000"));
        EntityManager second = manager(Map.of());
        assertThat(SearchRequestLimits.defaultPageSize(first)).isEqualTo(50);
        assertThat(SearchRequestLimits.defaultPageSize(second)).isEqualTo(20);
        assertThatCode(() -> SearchRequestLimits.validatePage(first, 0, 1500)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SearchRequestLimits.validatePage(second, 0, 1500)).hasMessageContaining("1000");
    }

    private EntityManager manager(Map<String, Object> properties) {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        when(em.getEntityManagerFactory()).thenReturn(factory);
        when(factory.getProperties()).thenReturn(properties);
        return em;
    }
}
