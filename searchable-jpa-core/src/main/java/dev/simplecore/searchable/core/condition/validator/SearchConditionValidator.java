package dev.simplecore.searchable.core.condition.validator;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata.SearchField;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.core.utils.SearchableValueParser;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.util.ClassUtils;
import org.springframework.validation.beanvalidation.LocaleContextMessageInterpolator;

/**
 * Validator for search conditions that ensures all search criteria are valid and consistent.
 * This validator checks various aspects of a search condition including:
 * <ul>
 *   <li>Pagination parameters validity</li>
 *   <li>Field existence and searchability</li>
 *   <li>Operator compatibility with field types</li>
 *   <li>Value type compatibility and constraints</li>
 *   <li>Collection values for IN/NOT_IN operators</li>
 * </ul>
 *
 * <p>The DTO is read by its fields, so a record or a class without accessors validates the same
 * way as a JavaBean. Bean Validation constraints declared on a field apply to every value searched
 * for on it, whichever operator carries the value.
 *
 * <p>Example usage:
 * <pre>
 * SearchCondition condition = ...;
 * SearchConditionValidator validator = new SearchConditionValidator(UserDTO.class, condition);
 * validator.validate(); // throws SearchableValidationException if any validation fails
 * </pre>
 */
public class SearchConditionValidator<D> {

    private static final ValidatorFactory VALIDATOR_FACTORY = localeAwareFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    private final Class<?> dtoClass;
    private final SearchCondition<D> condition;
    private final SearchableDtoMetadata metadata;

    /**
     * A violated constraint is reported in the locale of the request, as every message of the
     * library is, rather than in the default locale of the JVM.
     */
    private static ValidatorFactory localeAwareFactory() {
        Configuration<?> configuration = Validation.byDefaultProvider().configure();
        return configuration
                .messageInterpolator(new LocaleContextMessageInterpolator(configuration.getDefaultMessageInterpolator()))
                .buildValidatorFactory();
    }

    /**
     * Creates a new validator for the specified DTO class and search condition.
     *
     * @param dtoClass  the DTO class that defines the searchable fields
     * @param condition the search condition to validate
     */
    public SearchConditionValidator(Class<?> dtoClass, SearchCondition<D> condition) {
        this.dtoClass = dtoClass;
        this.condition = condition;
        this.metadata = SearchableDtoMetadata.of(dtoClass);
    }

    /**
     * Validates the entire search condition: pagination parameters, field existence, operator
     * compatibility, and value type compatibility.
     *
     * @throws SearchableValidationException if any validation fails
     */
    public void validate() {
        validatePagination();
        validateNodes(condition.getNodes());
    }

    /**
     * Validates the pagination parameters that are present. Either may be sent alone; the missing
     * one takes its default when the query is built.
     */
    private void validatePagination() {
        if (condition.getPage() != null && condition.getPage() < 0) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.pagination.page.invalid"));
        }
        if (condition.getSize() != null && condition.getSize() <= 0) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.pagination.size.invalid"));
        }
    }

    private void validateNodes(List<SearchCondition.Node> nodes) {
        if (nodes == null) {
            return;
        }
        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                validateCondition((SearchCondition.Condition) node);
            } else if (node instanceof SearchCondition.Group) {
                validateNodes(node.getNodes());
            }
        }
    }

    private void validateCondition(SearchCondition.Condition condition) {
        String fieldName = condition.getField();
        SearchField field = requireSearchableField(fieldName);
        SearchOperator operator = condition.getSearchOperator();
        if (operator == null || !field.allows(operator)) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.operator.not.allowed",
                            new Object[]{operator, fieldName, Arrays.toString(field.annotation().operators())}));
        }

        // A map, collection or array field is stored as text and searched as text.
        if (field.storedAsText()) {
            return;
        }

        validateValue(field.type(), condition.getValue(), operator, fieldName);
        boolean range = operator == SearchOperator.BETWEEN || operator == SearchOperator.NOT_BETWEEN;
        if (range) {
            if (condition.getValue2() == null) {
                throw new SearchableValidationException(
                        MessageUtils.getMessage("validator.between.value2.required", new Object[]{operator}));
            }
            validateSingleValue(field.type(), condition.getValue2(), fieldName);
        }

        // Validate only the searched property, not unrelated required DTO fields.
        if (operator == SearchOperator.IS_NULL || operator == SearchOperator.IS_NOT_NULL) {
            return;
        }
        if (condition.getValue() instanceof Collection<?> values) {
            for (Object value : values) {
                validateConstraints(fieldName, field.type(), value);
            }
        } else {
            validateConstraints(fieldName, field.type(), condition.getValue());
        }
        if (range) {
            validateConstraints(fieldName, field.type(), condition.getValue2());
        }
    }

    /**
     * @return the searchable field of that name
     * @throws SearchableValidationException when the DTO has no such field, or the field is not
     *         annotated as searchable
     */
    private SearchField requireSearchableField(String fieldName) {
        SearchField field = metadata.field(fieldName);
        if (field != null) {
            return field;
        }
        if (fieldName != null && SearchableFieldUtils.findFieldInHierarchy(dtoClass, fieldName) != null) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.field.not.searchable", new Object[]{fieldName}));
        }
        throw new SearchableValidationException(
                MessageUtils.getMessage("validator.field.not.found", new Object[]{fieldName, dtoClass.getSimpleName()}));
    }

    private void validateConstraints(String fieldName, Class<?> type, Object value) {
        // A field that carries no constraint has nothing to check.
        if (VALIDATOR.getConstraintsForClass(dtoClass).getConstraintsForProperty(fieldName) == null) {
            return;
        }
        Object converted = value instanceof String ? SearchableValueParser.parseValue((String) value, type) : value;
        Set<? extends ConstraintViolation<?>> violations = VALIDATOR.validateValue(dtoClass, fieldName, converted);
        if (!violations.isEmpty()) {
            throw new SearchableValidationException(violations.iterator().next().getMessage());
        }
    }

    private void validateValue(Class<?> targetType, Object value, SearchOperator operator, String fieldName) {
        if (value == null) {
            if (operator != SearchOperator.IS_NULL && operator != SearchOperator.IS_NOT_NULL) {
                throw new SearchableValidationException(
                        MessageUtils.getMessage("validator.value.null.not.allowed", new Object[]{operator}));
            }
            return;
        }
        if (operator == SearchOperator.IN || operator == SearchOperator.NOT_IN) {
            validateCollectionValue(targetType, value, fieldName);
            return;
        }
        validateSingleValue(targetType, value, fieldName);
    }

    private void validateCollectionValue(Class<?> targetType, Object value, String fieldName) {
        if (!(value instanceof Collection)) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.value.must.be.collection",
                            new Object[]{fieldName, value.getClass().getSimpleName()}));
        }
        Collection<?> collection = (Collection<?>) value;
        if (collection.isEmpty()) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.collection.empty", new Object[]{fieldName}));
        }
        for (Object item : collection) {
            validateSingleValue(targetType, item, fieldName);
        }
    }

    /**
     * Checks that a value is of the field's type, or text that can be read as it.
     */
    private void validateSingleValue(Class<?> targetType, Object value, String fieldName) {
        if (value == null || ClassUtils.resolvePrimitiveIfNecessary(targetType).isInstance(value)) {
            return;
        }
        if (!(value instanceof String)) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.value.type.conversion",
                            new Object[]{value.getClass().getSimpleName(), targetType.getSimpleName(), fieldName}));
        }
        try {
            SearchableValueParser.parseValue((String) value, targetType);
        } catch (SearchableParseException e) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.value.invalid", new Object[]{fieldName, e.getMessage()}), e);
        }
    }
}
