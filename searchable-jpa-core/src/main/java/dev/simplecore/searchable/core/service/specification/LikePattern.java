package dev.simplecore.searchable.core.service.specification;

/**
 * Escapes the characters a LIKE pattern gives meaning to, so a search value matches literally.
 */
final class LikePattern {

    /** The escape character every pattern predicate declares in its ESCAPE clause. */
    static final char ESCAPE = '\\';

    private LikePattern() {
    }

    /**
     * @param value the text to match literally
     * @param bracketOpensCharacterClass whether the database reads {@code [} as the start of a
     *                                   character class (Transact-SQL does); elsewhere a bracket is
     *                                   an ordinary character and escaping it is an error
     * @return the value with the escape character, {@code %}, {@code _} and, where it has a meaning,
     *         {@code [} escaped
     */
    static String escape(String value, boolean bracketOpensCharacterClass) {
        String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return bracketOpensCharacterClass ? escaped.replace("[", "\\[") : escaped;
    }
}
