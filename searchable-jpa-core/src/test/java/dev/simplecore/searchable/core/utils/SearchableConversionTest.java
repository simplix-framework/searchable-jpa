package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchableConversionTest {

    /** A value type the library has no parser of its own for. */
    public record Money(long cents) {
    }

    private final ConversionService original = SearchableConversionHolder.getConversionService();

    @AfterEach
    void restore() {
        SearchableConversionHolder.setConversionService(original);
    }

    @Test
    @DisplayName("a type nothing can read from text is refused by name")
    void anUnreadableTypeIsRefused() {
        assertThatThrownBy(() -> SearchableValueParser.parseValue("12.50", Money.class))
                .isInstanceOf(SearchableParseException.class).hasMessageContaining("Money");
        assertThatThrownBy(() -> SearchableValueParser.parseValue("PT5M", Duration.class))
                .isInstanceOf(SearchableParseException.class).hasMessageContaining("Duration");
    }

    @Test
    @DisplayName("a converter registered with the conversion service makes its type searchable")
    void aRegisteredConverterMakesATypeSearchable() {
        DefaultConversionService service = new DefaultConversionService();
        service.addConverter(String.class, Money.class,
                (Converter<String, Money>) text -> new Money(Math.round(Double.parseDouble(text) * 100)));
        service.addConverter(String.class, Duration.class, (Converter<String, Duration>) Duration::parse);
        SearchableConversionHolder.setConversionService(service);

        assertThat(SearchableValueParser.parseValue("12.50", Money.class)).isEqualTo(new Money(1250));
        assertThat(SearchableValueParser.parseValue("PT5M", Duration.class)).isEqualTo(Duration.ofMinutes(5));
        assertThatThrownBy(() -> SearchableValueParser.parseValue("not a number", Money.class))
                .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("text is a value of a text-compatible type, and of a type stored as text")
    void textCompatibleTypesKeepTheText() {
        assertThat(SearchableValueParser.parseValue("abc", Object.class)).isEqualTo("abc");
        assertThat(SearchableValueParser.parseValue("abc", CharSequence.class)).isEqualTo("abc");
        assertThat(SearchableValueParser.parseValue("abc", Map.class)).isEqualTo("abc");
        assertThat(SearchableValueParser.parseValue("abc", List.class)).isEqualTo("abc");
        assertThat(SearchableValueParser.parseValue("abc", String[].class)).isEqualTo("abc");
    }
}
