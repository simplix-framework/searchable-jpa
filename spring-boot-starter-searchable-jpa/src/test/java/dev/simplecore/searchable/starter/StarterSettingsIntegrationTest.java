package dev.simplecore.searchable.starter;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The starter's settings have to arrive at the EntityManagerFactory a real application builds,
 * because that is where the query code reads them. Asserting the Spring environment alone does not
 * prove that: the factory binds its properties before most configuration beans initialise.
 */
@SpringBootTest(classes = StarterTestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:starter-settings;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "searchable.max-page-size=5",
        "searchable.default-page-size=3",
        "searchable.hibernate.auto-fetch-collections=false",
        "searchable.hibernate.jdbc-batch-size=77"})
class StarterSettingsIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ThingService service;

    @Test
    @DisplayName("page limits and the collection switch reach the EntityManagerFactory")
    void searchSettingsReachTheEntityManagerFactory() {
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("searchable.max_page_size", "5")
                .containsEntry("searchable.default_page_size", "3")
                .containsEntry("searchable.auto_fetch_collections", "false");
    }

    @Test
    @DisplayName("Hibernate defaults reach the EntityManagerFactory, a configured one replacing the default")
    void hibernateDefaultsReachTheEntityManagerFactory() {
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("hibernate.jdbc.batch_size", "77")
                .containsEntry("hibernate.default_batch_fetch_size", "100")
                .containsEntry("hibernate.order_inserts", "true");
    }

    @Test
    @DisplayName("a page larger than the configured maximum is refused")
    void configuredMaximumIsEnforced() {
        SearchCondition<ThingSearchDto> tooLarge = SearchConditionBuilder.create(ThingSearchDto.class)
                .page(0).size(6).build();

        assertThatThrownBy(() -> service.findAllWithSearch(tooLarge))
                .isInstanceOf(SearchableValidationException.class);
        assertThat(service.findAllWithSearch(SearchConditionBuilder.create(ThingSearchDto.class)
                .page(0).size(5).build()).getSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("a condition without a size uses the configured default")
    void configuredDefaultIsUsed() {
        SearchCondition<ThingSearchDto> noSize = SearchConditionBuilder.create(ThingSearchDto.class)
                .where(w -> w.equals("name", "anything")).build();

        assertThat(service.findAllWithSearch(noSize).getSize()).isEqualTo(3);
    }
}
