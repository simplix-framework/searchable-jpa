package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The message of a violated DTO constraint is written in the locale of the request, whatever the
 * default locale of the JVM is.
 */
class ConstraintMessageLocaleTest {

    static class LabelDto {
        @SearchableField(operators = {SearchOperator.CONTAINS})
        @Size(min = 2, max = 100)
        private String label;
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static void searchOneCharacter() {
        new SearchableParamsParser<>(LabelDto.class).convert(Map.of("label.contains", "a"));
    }

    @Test
    @DisplayName("an English request reads the violation in English")
    void englishRequest() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThatThrownBy(ConstraintMessageLocaleTest::searchOneCharacter)
                .isInstanceOf(SearchableValidationException.class)
                .hasMessageContaining("size must be between 2 and 100");
    }

    @Test
    @DisplayName("a Korean request reads the same violation in Korean")
    void koreanRequest() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThatThrownBy(ConstraintMessageLocaleTest::searchOneCharacter)
                .isInstanceOf(SearchableValidationException.class)
                .hasMessageContaining("2")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("size must be between"));
    }
}
