package dev.simplecore.searchable.core.condition.validator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata.SearchField;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for ensuring proper usage of {@link SearchableField} annotations in search conditions.
 * This validator checks three main aspects:
 * <ul>
 *   <li>Field existence: Verifies that all fields used in the search condition are properly annotated with @SearchableField</li>
 *   <li>Operator compatibility: Ensures that search operators used are allowed for each field</li>
 *   <li>Sort field validity: Confirms that fields used for sorting are marked as sortable</li>
 * </ul>
 *
 * <p>The validator supports inheritance by checking fields in the entire class hierarchy, and
 * reports every offending field, operator and sort field of a condition in one message.
 *
 * <p>Example usage:
 * <pre>
 * SearchCondition condition = ...;
 * SearchableFieldValidator validator = new SearchableFieldValidator(UserDTO.class, condition);
 * validator.validate(); // throws SearchableValidationException if validation fails
 * </pre>
 */
public class SearchableFieldValidator<D> {

    private final Class<D> dtoClass;
    private final SearchCondition<D> condition;

    /**
     * Creates a new validator for the specified DTO class and search condition.
     *
     * @param dtoClass  the DTO class that defines the searchable fields
     * @param condition the search condition to validate
     */
    public SearchableFieldValidator(Class<D> dtoClass, SearchCondition<D> condition) {
        this.dtoClass = dtoClass;
        this.condition = condition;
    }

    /**
     * Validates that all fields used in the SearchCondition are properly annotated with @SearchableField
     * and their operators are allowed.
     *
     * @throws SearchableValidationException if any validation fails, with a message listing the
     *                                       fields not annotated with @SearchableField, the operators
     *                                       not allowed for their fields, and the sort fields not
     *                                       marked as sortable
     */
    public void validate() {
        // Skip validation if no DTO class is specified
        if (dtoClass == null) {
            return;
        }
        SearchableDtoMetadata metadata = SearchableDtoMetadata.of(dtoClass);

        Set<String> invalidFields = new LinkedHashSet<>();
        Set<String> invalidOperators = new LinkedHashSet<>();
        Set<String> invalidSortFields = new LinkedHashSet<>();

        validateNodes(metadata, condition.getNodes(), invalidFields, invalidOperators);
        validateSort(metadata, condition.getSort(), invalidSortFields);

        StringBuilder errorMessage = new StringBuilder();
        if (!invalidFields.isEmpty()) {
            errorMessage.append(MessageUtils.getMessage("validator.field.not.found",
                    new Object[]{String.join(", ", invalidFields), dtoClass.getSimpleName()}));
        }
        if (!invalidOperators.isEmpty()) {
            if (errorMessage.length() > 0) errorMessage.append("\n");
            errorMessage.append(MessageUtils.getMessage("validator.field.not.supported",
                    new Object[]{String.join(", ", invalidOperators)}));
        }
        if (!invalidSortFields.isEmpty()) {
            if (errorMessage.length() > 0) errorMessage.append("\n");
            errorMessage.append(MessageUtils.getMessage("validator.field.not.sortable",
                    new Object[]{String.join(", ", invalidSortFields)}));
        }
        if (errorMessage.length() > 0) {
            throw new SearchableValidationException(errorMessage.toString());
        }
    }

    private void validateNodes(SearchableDtoMetadata metadata, List<SearchCondition.Node> nodes,
                               Set<String> invalidFields, Set<String> invalidOperators) {
        if (nodes == null) {
            return;
        }
        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition condition) {
                SearchField field = metadata.field(condition.getField());
                if (field == null) {
                    invalidFields.add(String.valueOf(condition.getField()));
                } else if (!field.allows(condition.getSearchOperator())) {
                    invalidOperators.add(condition.getField() + "." + condition.getSearchOperator());
                }
            } else if (node instanceof SearchCondition.Group) {
                validateNodes(metadata, node.getNodes(), invalidFields, invalidOperators);
            }
        }
    }

    private void validateSort(SearchableDtoMetadata metadata, SearchCondition.Sort sort, Set<String> invalidSortFields) {
        if (sort == null || sort.getOrders() == null) {
            return;
        }
        for (SearchCondition.Order order : sort.getOrders()) {
            SearchField field = metadata.field(order.getField());
            if (field == null || !field.annotation().sortable()) {
                invalidSortFields.add(String.valueOf(order.getField()));
            }
        }
    }
}
