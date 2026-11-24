package dev.simplecore.searchable.core.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.searchable.core.exception.SearchableException;

/**
 * Reads and writes a {@link SearchCondition} as JSON for {@link SearchCondition#fromJson} and
 * {@link SearchCondition#toJson}.
 */
final class SearchConditionMapper {

    private static final ObjectMapper MAPPER = newObjectMapper();

    private SearchConditionMapper() {
    }

    private static ObjectMapper newObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }

    /**
     * @throws SearchableException when the JSON is well formed but is not a valid search
     */
    static <T> SearchCondition<T> read(String json, Class<T> dtoClass) throws JsonProcessingException {
        JavaType type = MAPPER.getTypeFactory().constructParametricType(SearchCondition.class, dtoClass);
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonMappingException exception) {
            if (exception.getCause() instanceof SearchableException invalid) {
                throw invalid;
            }
            throw exception;
        }
    }

    static String write(SearchCondition<?> condition) throws JsonProcessingException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(condition);
    }
}
