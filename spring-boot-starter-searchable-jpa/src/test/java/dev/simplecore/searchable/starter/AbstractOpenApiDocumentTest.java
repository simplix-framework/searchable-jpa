package dev.simplecore.searchable.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the document SpringDoc serves for the test application, as a client or a code generator
 * reads it. The same checks run for an OpenAPI 3.0 and a 3.1 document, because the two are written
 * from different fields of the same schema objects.
 */
abstract class AbstractOpenApiDocumentTest {

    private static final List<String> QUERY_SEARCH_PATHS = List.of("/things/legacy", "/things/raw", "/things/resolved");
    private static final String COMPONENT_PREFIX = "#/components/schemas/";

    @Autowired
    private WebApplicationContext context;

    private JsonNode document;

    /** @return the leading digits of the {@code openapi} version this document must carry */
    abstract String expectedVersion();

    @BeforeEach
    void fetchDocument() throws Exception {
        String body = MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        document = new ObjectMapper().readTree(body);
    }

    private JsonNode parameters(String path) {
        return document.path("paths").path(path).path("get").path("parameters");
    }

    private JsonNode parameter(String path, String name) {
        for (JsonNode parameter : parameters(path)) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("no parameter " + name + " documented for " + path);
    }

    private static void collectReferences(JsonNode node, List<String> references) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey().equals("$ref") && entry.getValue().isTextual()) {
                    references.add(entry.getValue().asText());
                } else {
                    collectReferences(entry.getValue(), references);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectReferences(child, references));
        }
    }

    private static boolean mentions(JsonNode node, String fieldName) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey().equals(fieldName) || mentions(entry.getValue(), fieldName)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (mentions(child, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("the document is written in the expected OpenAPI version")
    void documentVersion() {
        assertThat(document.path("openapi").asText()).startsWith(expectedVersion());
    }

    @Test
    @DisplayName("every schema reference points at a schema the document contains")
    void everyReferenceResolves() {
        List<String> references = new ArrayList<>();
        collectReferences(document, references);
        TreeSet<String> dangling = new TreeSet<>();
        for (String reference : references) {
            if (reference.startsWith(COMPONENT_PREFIX)
                    && !document.path("components").path("schemas").has(reference.substring(COMPONENT_PREFIX.length()))) {
                dangling.add(reference);
            }
        }
        assertThat(references).as("the document uses references at all").isNotEmpty();
        assertThat(dangling).isEmpty();
    }

    @Test
    @DisplayName("every search parameter says what type it is")
    void everySearchParameterIsTyped() {
        List<String> untyped = new ArrayList<>();
        for (String path : QUERY_SEARCH_PATHS) {
            assertThat(parameters(path)).as("parameters of %s", path).isNotEmpty();
            for (JsonNode parameter : parameters(path)) {
                JsonNode schema = parameter.path("schema");
                if (!schema.has("type") && !schema.has("$ref")) {
                    untyped.add(path + " " + parameter.path("name").asText() + " " + schema);
                }
                if ("array".equals(schema.path("type").asText()) && !schema.path("items").has("type")) {
                    untyped.add(path + " " + parameter.path("name").asText() + " items " + schema);
                }
            }
        }
        assertThat(untyped).isEmpty();
    }

    @Test
    @DisplayName("each field and operator is a typed parameter of its own")
    void fieldParametersCarryTheirTypes() {
        for (String path : QUERY_SEARCH_PATHS) {
            assertThat(parameter(path, "id.equals").path("schema").path("type").asText()).isEqualTo("integer");
            assertThat(parameter(path, "name.contains").path("schema").path("type").asText()).isEqualTo("string");
            assertThat(parameter(path, "page").path("schema").path("type").asText()).isEqualTo("integer");
            JsonNode kind = parameter(path, "kind.equals").path("schema");
            assertThat(kind.has("$ref")).as("an enum is listed inline").isFalse();
            assertThat(kind.path("enum")).extracting(JsonNode::asText).containsExactly("A", "B");
        }
    }

    @Test
    @DisplayName("a null check offers true as its only value")
    void nullCheckOffersOnlyTrue() {
        JsonNode schema = parameter("/things/raw", "name.isNull").path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("boolean");
        assertThat(schema.path("enum")).extracting(JsonNode::asBoolean).containsExactly(true);
    }

    @Test
    @DisplayName("sort is documented as the one comma-separated value the parser reads")
    void sortIsOneCommaSeparatedValue() {
        for (String path : QUERY_SEARCH_PATHS) {
            JsonNode sort = parameter(path, "sort");
            assertThat(sort.path("explode").asBoolean(true)).isFalse();
            assertThat(sort.path("schema").path("type").asText()).isEqualTo("array");
            assertThat(sort.path("schema").path("items").path("type").asText()).isEqualTo("string");
        }
    }

    @Test
    @DisplayName("the parameter that only carries the raw values is not documented")
    void rawParameterCarrierIsNotDocumented() {
        assertThat(parameters("/things/legacy")).extracting(p -> p.path("name").asText()).doesNotContain("params");
        assertThat(parameters("/things/raw")).extracting(p -> p.path("name").asText()).doesNotContain("filters");
    }

    @Test
    @DisplayName("a search the starter resolves is documented with the parameters of a raw one, and nothing else")
    void resolvedSearchIsDocumentedLikeARawOne() {
        List<String> raw = new ArrayList<>();
        parameters("/things/raw").forEach(parameter -> raw.add(parameter.path("name").asText()));
        List<String> resolved = new ArrayList<>();
        parameters("/things/resolved").forEach(parameter -> resolved.add(parameter.path("name").asText()));

        assertThat(resolved).isNotEmpty().containsExactlyInAnyOrderElementsOf(raw);
    }

    @Test
    @DisplayName("a description the application wrote is kept in front of the field summary")
    void writtenDescriptionIsKept() {
        for (String path : QUERY_SEARCH_PATHS) {
            String description = document.path("paths").path(path).path("get").path("description").asText();
            assertThat(description).startsWith(ThingController.WRITTEN_DESCRIPTION).contains("Searchable Fields");
        }
    }

    @Test
    @DisplayName("the request body example is a JSON object that shows no internal mapping")
    void requestBodyExampleIsStructured() {
        JsonNode body = document.path("paths").path("/things/search").path("post").path("requestBody");
        JsonNode example = body.path("content").path("application/json").path("examples").path("example").path("value");

        assertThat(example.isObject()).as("example is structured, not a string").isTrue();
        assertThat(example.path("conditions").isArray()).isTrue();
        assertThat(mentions(example, "entityField")).isFalse();
        assertThat(body.path("content").path("application/json").path("schema").has("type")
                || body.path("content").path("application/json").path("schema").has("$ref")).isTrue();
    }
}
