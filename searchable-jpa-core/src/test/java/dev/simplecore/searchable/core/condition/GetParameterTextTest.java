package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.exception.SearchableException;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Text sent for a String field is searched for as sent. A query parameter and a JSON body carrying
 * the same text ask the same question.
 */
class GetParameterTextTest {

    public static class TextDto {
        @SearchableField(operators = {EQUALS, CONTAINS})
        private String name;
    }

    private static Object valueOf(SearchCondition<?> condition) {
        return ((SearchCondition.Condition) condition.getNodes().get(0)).getValue();
    }

    private static Object fromParameter(String operator, String text) {
        return valueOf(new SearchableParamsParser<>(TextDto.class).convert(Map.of("name." + operator, text)));
    }

    private static Object fromJson(String operator, String text) throws Exception {
        return valueOf(SearchCondition.fromJson("{\"conditions\":[{\"field\":\"name\",\"searchOperator\":\""
                + operator + "\",\"value\":\"" + text + "\"}]}", TextDto.class));
    }

    @Test
    @DisplayName("the word null, a compatibility character and surrounding spaces stay as sent")
    void textIsKeptAsSent() throws Exception {
        // U+321C is one character that compatibility normalisation would spell out as three.
        for (String text : new String[]{"Null", "null", "\u321C\uC0BC\uC131", " padded "}) {
            for (String operator : new String[]{"equals", "contains"}) {
                assertThat(fromParameter(operator, text)).as("%s as a query parameter of %s", text, operator).isEqualTo(text);
                assertThat(fromJson(operator, text)).as("%s in JSON for %s", text, operator).isEqualTo(text);
            }
        }
    }

    @Test
    @DisplayName("a lenient parser leaves out what is not a search parameter of the DTO and still refuses a wrong one")
    void aLenientParserSkipsForeignParameters() {
        SearchableParamsParser<TextDto> lenient = new SearchableParamsParser<>(TextDto.class, true);

        SearchCondition<TextDto> condition = lenient.convert(
                Map.of("name.equals", "a", "utm_source", "mail", "_", "17", "other.equals", "x"));
        assertThat(condition.getNodes()).hasSize(1);
        assertThat(valueOf(condition)).isEqualTo("a");
        assertThat(lenient.convert(Map.of("utm_source", "mail", "other.contains", "x")).getNodes()).isEmpty();

        // Addressed to a field of the DTO, so read, and refused when it is wrong.
        assertThatThrownBy(() -> lenient.convert(Map.of("name.nope", "a"))).isInstanceOf(SearchableParseException.class);
        assertThatThrownBy(() -> lenient.convert(Map.of("name.startsWith", "a"))).isInstanceOf(SearchableException.class);
    }

    @Test
    @DisplayName("a parser refuses a parameter it cannot read unless told to be lenient")
    void aStrictParserRefusesForeignParameters() {
        SearchableParamsParser<TextDto> strict = new SearchableParamsParser<>(TextDto.class);

        assertThatThrownBy(() -> strict.convert(Map.of("utm_source", "mail"))).isInstanceOf(SearchableParseException.class);
        assertThatThrownBy(() -> strict.convert(Map.of("other.equals", "x"))).isInstanceOf(SearchableException.class);
    }
}
