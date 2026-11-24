package dev.simplecore.searchable.core.service.specification;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternTest {

    private static final String VALUE = "50%_off [new]\\";

    @Test
    @DisplayName("the wildcards and the escape character are escaped on every database")
    void wildcardsAreEscaped() {
        assertThat(LikePattern.escape(VALUE, false)).isEqualTo("50\\%\\_off [new]\\\\");
    }

    @Test
    @DisplayName("a bracket is escaped only where it opens a character class")
    void bracketIsEscapedWhereItHasAMeaning() {
        assertThat(LikePattern.escape(VALUE, true)).isEqualTo("50\\%\\_off \\[new]\\\\");
    }

    @Test
    @DisplayName("only the Transact-SQL dialects read a bracket as a character class")
    void transactSqlDialectsReadBrackets() {
        assertThat(DatabaseTraits.bracketOpensCharacterClass(new SQLServerDialect())).isTrue();
        assertThat(DatabaseTraits.bracketOpensCharacterClass(new SybaseDialect())).isTrue();
        assertThat(DatabaseTraits.bracketOpensCharacterClass(new H2Dialect())).isFalse();
        assertThat(DatabaseTraits.bracketOpensCharacterClass(new PostgreSQLDialect())).isFalse();
        assertThat(DatabaseTraits.bracketOpensCharacterClass(new MySQLDialect())).isFalse();
    }
}
