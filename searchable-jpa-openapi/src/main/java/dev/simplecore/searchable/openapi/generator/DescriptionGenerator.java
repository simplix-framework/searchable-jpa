package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import io.swagger.v3.oas.models.Operation;
import java.lang.reflect.Field;
import java.util.StringJoiner;

public class DescriptionGenerator {

    /**
     * Adds the summary of searchable fields to the operation's description. A description the
     * application wrote stays in front of it.
     */
    public static void customizeOperation(Operation operation, Class<?> dtoClass) {
        String summary = generateDescription(dtoClass);
        String written = operation.getDescription();
        if (written == null || written.isBlank()) {
            operation.setDescription(summary);
        } else if (!written.contains(summary)) {
            operation.setDescription(written + "\n\n" + summary);
        }
    }

    static String generateDescription(Class<?> dtoClass) {
        StringBuilder description = new StringBuilder();
        appendSearchFieldSummary(dtoClass, description);
        return description.toString();
    }

    private static void appendSearchFieldSummary(Class<?> dtoClass, StringBuilder description) {
        description.append("**Searchable Fields:** ");

        StringJoiner fieldJoiner = new StringJoiner(", ");
        for (Field field : SearchableFieldUtils.getSearchableFields(dtoClass)) {
            SearchableField searchOp = field.getAnnotation(SearchableField.class);
            StringJoiner opJoiner = new StringJoiner("|");
            for (SearchOperator op : SearchableFieldUtils.getAllowedOperators(searchOp)) {
                opJoiner.add(op.getName());
            }
            String sortIndicator = searchOp.sortable() ? " [sortable]" : "";
            fieldJoiner.add(String.format("`%s` (%s)%s", field.getName(), opJoiner, sortIndicator));
        }
        description.append(fieldJoiner);
    }
}
