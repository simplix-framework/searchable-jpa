package dev.simplecore.searchable.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.exception.SearchableException;
import dev.simplecore.searchable.core.utils.SearchableConversionHolder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A controller parameter {@code @SearchableParams SearchCondition<D>} in an application that sets
 * none of the starter's web options: the search arrives resolved, and an invalid request is left to
 * the application's own error handling.
 */
@SpringBootTest(classes = StarterTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:starter-web;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class StarterWebIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ThingRepository repository;

    private MockMvc mvc;

    @BeforeEach
    void loadFixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
        repository.saveAll(List.of(thing(1L, "alpha", Thing.Kind.A), thing(2L, "beta", Thing.Kind.B),
                thing(3L, "gamma", Thing.Kind.A), thing(4L, "delta", Thing.Kind.B)));
    }

    static Thing thing(Long id, String name, Thing.Kind kind) {
        Thing thing = new Thing();
        thing.setId(id);
        thing.setName(name);
        thing.setKind(kind);
        return thing;
    }

    private List<String> names(String query) throws Exception {
        String body = mvc.perform(get("/things/resolved" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = new ArrayList<>();
        for (JsonNode name : new ObjectMapper().readTree(body)) {
            names.add(name.asText());
        }
        return names;
    }

    @Test
    @DisplayName("the query string arrives at the controller as the search it describes")
    void theQueryStringIsResolved() throws Exception {
        assertThat(names("")).containsExactly("alpha", "beta", "gamma", "delta");
        assertThat(names("?name.contains=a&kind.equals=A&sort=name.desc")).containsExactly("gamma", "alpha");
        assertThat(names("?kind.in=A,B&sort=name.asc&page=1&size=2")).containsExactly("delta", "gamma");
    }

    @Test
    @DisplayName("a parameter sent more than once is read as its values listed")
    void repeatedParametersAreListed() throws Exception {
        assertThat(names("?sort=name.desc")).containsExactly("gamma", "delta", "beta", "alpha");
        assertThat(names("?kind.in=A&kind.in=B&sort=name.asc&size=3")).containsExactly("alpha", "beta", "delta");
    }

    @Test
    @DisplayName("without the problem-detail option an invalid search reaches the application as the library's exception")
    void anInvalidSearchIsLeftToTheApplication() {
        for (String query : List.of("?name.startsWith=a", "?utm_source=mail", "?id.equals=abc", "?sort=name.sideways")) {
            assertThatThrownBy(() -> mvc.perform(get("/things/resolved" + query))).as(query)
                    .hasCauseInstanceOf(SearchableException.class);
        }
    }

    @Test
    @DisplayName("search values are converted by the conversion service Spring MVC uses")
    void theMvcConversionServiceIsUsed() {
        assertThat(SearchableConversionHolder.getConversionService())
                .isSameAs(context.getBean("mvcConversionService"));
    }
}
