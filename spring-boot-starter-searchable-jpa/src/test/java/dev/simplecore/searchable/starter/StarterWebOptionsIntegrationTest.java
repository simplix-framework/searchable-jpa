package dev.simplecore.searchable.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The starter's options switched away from their defaults: invalid searches answered with a
 * problem detail, foreign query parameters left out, tighter request limits, the per-unit write and
 * aggregate settings, and an application message replacing the library's.
 */
@SpringBootTest(classes = StarterTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:starter-web-options;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "searchable.web.problem-details.enabled=true",
        "searchable.params.ignore-unknown=true",
        "searchable.limits.max-in-values=2",
        "searchable.limits.max-sort-fields=1",
        "searchable.in-clause-batch-size=50",
        "searchable.write.chunk-size=25",
        "searchable.write.require-filter=true",
        "searchable.aggregate.max-groups=7",
        "searchable.bucket.max-buckets=9"})
@Import(StarterWebOptionsIntegrationTest.ApplicationMessages.class)
class StarterWebOptionsIntegrationTest {

    static final String APPLICATION_TITLE = "The search could not be read";

    @TestConfiguration
    static class ApplicationMessages {
        @Bean
        MessageSource messageSource() {
            StaticMessageSource messages = new StaticMessageSource();
            messages.addMessage("web.problem.invalid.search", Locale.ENGLISH, APPLICATION_TITLE);
            return messages;
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ThingRepository repository;
    @Autowired
    private ThingService service;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private MockMvc mvc;

    @BeforeEach
    void loadFixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
        repository.saveAll(List.of(StarterWebIntegrationTest.thing(1L, "alpha", Thing.Kind.A),
                StarterWebIntegrationTest.thing(2L, "beta", Thing.Kind.B)));
    }

    @AfterAll
    static void restoreProcessWideSettings() {
        MessageUtils.init(null);
        SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(SearchRequestLimits.MAX_NODES,
                SearchRequestLimits.MAX_DEPTH, SearchRequestLimits.MAX_IN_VALUES, SearchRequestLimits.MAX_SORT_FIELDS));
    }

    private JsonNode problem(String query) throws Exception {
        String body = mvc.perform(get("/things/resolved" + query).locale(Locale.ENGLISH))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    @Test
    @DisplayName("an invalid search is answered with status 400 and says what was wrong")
    void anInvalidSearchIsAProblemDetail() throws Exception {
        assertThat(problem("?name.startsWith=a").path("status").asInt()).isEqualTo(400);
        assertThat(problem("?id.equals=abc").path("detail").asText()).contains("id");
        assertThat(problem("?sort=name.sideways").path("detail").asText()).contains("name.sideways");
    }

    @Test
    @DisplayName("a message the application defines replaces the library's, and the others stay")
    void applicationMessagesComeFirst() throws Exception {
        JsonNode problem = problem("?name.nope=a");

        assertThat(problem.path("title").asText()).isEqualTo(APPLICATION_TITLE);
        assertThat(problem.path("detail").asText()).contains("nope");
    }

    @Test
    @DisplayName("a query parameter that is not a search parameter is left out")
    void foreignParametersAreLeftOut() throws Exception {
        mvc.perform(get("/things/resolved?utm_source=mail&_=17&other.equals=x&name.equals=alpha"))
                .andExpect(status().isOk()).andExpect(content().json("[\"alpha\"]"));
    }

    @Test
    @DisplayName("the configured request limits are enforced")
    void requestLimitsAreEnforced() throws Exception {
        mvc.perform(get("/things/resolved?kind.in=A,B")).andExpect(status().isOk());
        assertThat(problem("?kind.in=A,B,A").path("detail").asText()).contains("2");
        assertThat(problem("?sort=name.asc,id.desc").path("detail").asText()).contains("1");
    }

    @Test
    @DisplayName("the write, aggregate and bucket settings reach the EntityManagerFactory")
    void unitSettingsReachTheEntityManagerFactory() {
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("searchable.in_clause_batch_size", "50")
                .containsEntry("searchable.write_chunk_size", "25")
                .containsEntry("searchable.require_write_filter", "true")
                .containsEntry("searchable.max_aggregate_groups", "7")
                .containsEntry("searchable.max_buckets", "9");
    }

    @Test
    @DisplayName("a search-based delete that nothing narrows is refused")
    void anUnfilteredWriteIsRefused() {
        assertThatThrownBy(() -> service.deleteWithSearch(new SearchCondition<>()))
                .isInstanceOf(SearchableValidationException.class);
        assertThat(repository.count()).isEqualTo(2);
    }
}
