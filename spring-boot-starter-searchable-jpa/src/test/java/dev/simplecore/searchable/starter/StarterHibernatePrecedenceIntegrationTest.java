package dev.simplecore.searchable.starter;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The library's Hibernate values are defaults: what the application sets under
 * {@code spring.jpa.properties} wins, and switching the defaults off leaves Hibernate untouched
 * while the search limits stay in force.
 */
@SpringBootTest(classes = StarterTestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:starter-precedence;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.batch_size=500",
        "spring.jpa.properties.searchable.max_page_size=40",
        "searchable.max-page-size=50",
        "searchable.hibernate.auto-optimization=false"})
class StarterHibernatePrecedenceIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("an application's own spring.jpa.properties value is kept")
    void applicationValueWins() {
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("hibernate.jdbc.batch_size", "500")
                .containsEntry("searchable.max_page_size", "40");
    }

    @Test
    @DisplayName("auto-optimization=false adds no Hibernate default but keeps the search limits")
    void disabledOptimizationAddsNoHibernateDefault() {
        assertThat(entityManagerFactory.getProperties())
                .doesNotContainKey("hibernate.order_inserts")
                .doesNotContainKey("hibernate.default_batch_fetch_size")
                .containsEntry("searchable.default_page_size", "20");
    }
}
