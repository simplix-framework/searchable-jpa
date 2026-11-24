package dev.simplecore.searchable.openapi.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the allowed values out of a regular expression that is nothing but a list of them.
 */
final class PatternAlternatives {

    private static final int MAX_ALTERNATIVES = 20;

    /** A list of literal alternatives, with or without anchors and one enclosing group. */
    private static final Pattern LITERAL_ALTERNATIVES =
            Pattern.compile("\\^?\\(?([\\w -]+(?:\\|[\\w -]+)+)\\)?\\$?");

    /** One character out of a listed set, with or without anchors. */
    private static final Pattern SINGLE_CHARACTER_SET = Pattern.compile("\\^?\\[(\\w+)]\\$?");

    private PatternAlternatives() {
    }

    /**
     * @param regexp a regular expression
     * @return the values it allows when it is a plain list ({@code ACTIVE|INACTIVE}) or one character
     *         out of a set ({@code [YN]}); an empty list for any other expression
     */
    static List<String> of(String regexp) {
        List<String> alternatives = new ArrayList<>();
        Matcher literals = LITERAL_ALTERNATIVES.matcher(regexp);
        Matcher characters = SINGLE_CHARACTER_SET.matcher(regexp);
        if (literals.matches()) {
            alternatives.addAll(Arrays.asList(literals.group(1).split("\\|")));
        } else if (characters.matches()) {
            characters.group(1).chars().forEach(c -> alternatives.add(String.valueOf((char) c)));
        }
        return alternatives.size() > MAX_ALTERNATIVES ? List.of() : alternatives;
    }
}
