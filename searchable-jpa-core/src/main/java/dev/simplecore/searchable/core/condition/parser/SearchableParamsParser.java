package dev.simplecore.searchable.core.condition.parser;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.ChainedCondition;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata.SearchField;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.core.utils.SearchableValueParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import org.springframework.util.StringUtils;

/**
 * Parser for converting URL parameters into SearchCondition objects.
 * Supports parsing of field conditions, sorting, and pagination parameters.
 *
 * <p>Format:
 * - Field conditions: field.operator=value or field.operator=value1,value2
 * - Sort: sort=field.asc,field.desc
 * - Pagination: page=0&size=10
 * - Keyword: keyword=text, matched by CONTAINS against every field marked
 *   {@code @SearchableField(keyword = true)}, OR-combined, and AND-combined with the other conditions
 *
 * <p>Example:
 * name.equals=John&age.greaterThan=20&sort=name.asc,age.desc&page=0&size=10
 *
 * @param <D>
 */
public class SearchableParamsParser<D> {
    private static final String FIELD_OPERATOR_PATTERN = "^([^.]+(?:\\.[^.]+)*)\\.([^=]+)$";
    private static final String SORT_DIRECTION_PATTERN = "^([^.]+(?:\\.[^.]+)*?)\\.(asc|desc)$";
    private static final Pattern fieldOperatorPattern = Pattern.compile(FIELD_OPERATOR_PATTERN);
    private static final Pattern sortDirectionPattern = Pattern.compile(SORT_DIRECTION_PATTERN);

    /** The reserved parameter carrying the keyword search; it has no operator suffix. */
    public static final String KEYWORD_PARAM = "keyword";

    private final Class<D> dtoClass;
    private final boolean ignoreUnknownParameters;

    public SearchableParamsParser(@NonNull Class<D> dtoClass) {
        this(dtoClass, false);
    }

    /**
     * @param dtoClass the search DTO the parameters are read against
     * @param ignoreUnknownParameters whether a parameter that is not a search parameter of the DTO is
     *                                left out instead of being refused: one without a
     *                                {@code field.operator} name, or one naming a field the DTO does
     *                                not declare. A request then may carry parameters meant for
     *                                something else. A parameter that names a field of the DTO is
     *                                always read, and refused when it is wrong.
     */
    public SearchableParamsParser(@NonNull Class<D> dtoClass, boolean ignoreUnknownParameters) {
        this.dtoClass = dtoClass;
        this.ignoreUnknownParameters = ignoreUnknownParameters;
    }

    /**
     * Converts URL parameters into a SearchCondition.
     *
     * @param params Map of URL parameters
     * @return SearchCondition object
     * @throws SearchableParseException if parsing fails
     */
    public SearchCondition<D> convert(@NonNull Map<String, String> params) {
        SearchConditionBuilder<D> builder = SearchConditionBuilder.create(dtoClass);

        // Parse conditions
        Map<String, String> conditions = new HashMap<>();
        String[] keyword = new String[1];

        params.forEach((key, value) -> {
            switch (key) {
                case "sort":
                    parseSort(builder, value);
                    break;
                case "page":
                    validateNumeric(value, "page");
                    builder.page(Integer.parseInt(value));
                    break;
                case "size":
                    validateNumeric(value, "size");
                    builder.size(Integer.parseInt(value));
                    break;
                case KEYWORD_PARAM:
                    keyword[0] = value;
                    break;
                default:
                    if (!ignoreUnknownParameters || namesAFieldOfTheDto(key)) {
                        conditions.put(key, value);
                    }
                    break;
            }
        });

        List<String> keywordFields = StringUtils.hasText(keyword[0]) ? keywordFields() : List.of();

        // Parse conditions (AND), with the keyword's OR group as one more AND-ed member
        if (!conditions.isEmpty() || !keywordFields.isEmpty()) {
            String text = StringUtils.hasText(keyword[0]) ? keyword[0].trim() : null;
            builder.where(whereBuilder -> {
                conditions.forEach((key, value) -> parseCondition(whereBuilder, key, value));
                if (!keywordFields.isEmpty()) {
                    // A nested group, never inlined: inlined, the OR chain folds left to right
                    // into "(conditions AND first) OR second OR ...", and every condition
                    // before it, a caller's range included, stops narrowing the result.
                    whereBuilder.and(group -> {
                        ChainedCondition chain = group.contains(keywordFields.get(0), text);
                        for (int i = 1; i < keywordFields.size(); i++) {
                            chain = chain.orContains(keywordFields.get(i), text);
                        }
                    });
                }
            });
        }

        return builder.build();
    }

    /**
     * The fields the keyword search matches against, in declaration order.
     *
     * @throws SearchableParseException when the DTO marks no field, since a keyword that matched
     *         nothing would silently return every record
     */
    private List<String> keywordFields() {
        List<String> fields = new ArrayList<>();
        for (SearchField field : SearchableDtoMetadata.of(dtoClass).keywordFields()) {
            if (field.type() != String.class || !field.allows(SearchOperator.CONTAINS)) {
                throw new SearchableParseException(MessageUtils.getMessage(
                        "parser.keyword.field.invalid", new Object[]{field.name()}));
            }
            fields.add(field.name());
        }
        if (fields.isEmpty()) {
            throw new SearchableParseException(MessageUtils.getMessage(
                    "parser.keyword.unsupported", new Object[]{dtoClass.getSimpleName()}));
        }
        return fields;
    }

    private boolean namesAFieldOfTheDto(String key) {
        Matcher matcher = fieldOperatorPattern.matcher(key);
        return matcher.matches() && SearchableDtoMetadata.of(dtoClass).field(matcher.group(1)) != null;
    }

    private void validateNumeric(String value, String field) {
        try {
            int num = Integer.parseInt(value);
            if (num < 0) {
                throw new SearchableParseException(MessageUtils.getMessage("parser.numeric.nonnegative", new Object[]{field}));
            }
        } catch (NumberFormatException e) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.numeric.invalid", new Object[]{field}));
        }
    }

    private void parseSort(SearchConditionBuilder<D> builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        builder.sort(sortBuilder -> {
            String[] sortOrders = value.split(",");
            for (String order : sortOrders) {
                Matcher matcher = sortDirectionPattern.matcher(order.trim());
                if (matcher.matches()) {
                    String field = matcher.group(1);
                    String direction = matcher.group(2);
                    if ("asc".equals(direction)) {
                        sortBuilder.asc(field);
                    } else {
                        sortBuilder.desc(field);
                    }
                } else {
                    throw new SearchableParseException(MessageUtils.getMessage("parser.sort.invalid", new Object[]{order}));
                }
            }
        });
    }

    private void parseCondition(FirstCondition whereBuilder, String key, String value) {
        Matcher matcher = fieldOperatorPattern.matcher(key);
        if (!matcher.matches()) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.field.invalid", new Object[]{key}));
        }

        String field = matcher.group(1);
        String operatorStr = matcher.group(2);

        SearchOperator operator = SearchOperator.fromName(operatorStr);
        if (operator == null) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.operator.invalid", new Object[]{operatorStr}));
        }

        switch (operator) {
            case EQUALS:
                whereBuilder.equals(field, parseValue(field, value));
                break;
            case NOT_EQUALS:
                whereBuilder.notEquals(field, parseValue(field, value));
                break;
            case GREATER_THAN:
                whereBuilder.greaterThan(field, parseValue(field, value));
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                whereBuilder.greaterThanOrEqualTo(field, parseValue(field, value));
                break;
            case LESS_THAN:
                whereBuilder.lessThan(field, parseValue(field, value));
                break;
            case LESS_THAN_OR_EQUAL_TO:
                whereBuilder.lessThanOrEqualTo(field, parseValue(field, value));
                break;
            case CONTAINS:
                whereBuilder.contains(field, requirePatternValue(operatorStr, value));
                break;
            case NOT_CONTAINS:
                whereBuilder.notContains(field, requirePatternValue(operatorStr, value));
                break;
            case STARTS_WITH:
                whereBuilder.startsWith(field, requirePatternValue(operatorStr, value));
                break;
            case NOT_STARTS_WITH:
                whereBuilder.notStartsWith(field, requirePatternValue(operatorStr, value));
                break;
            case ENDS_WITH:
                whereBuilder.endsWith(field, requirePatternValue(operatorStr, value));
                break;
            case NOT_ENDS_WITH:
                whereBuilder.notEndsWith(field, requirePatternValue(operatorStr, value));
                break;
            case IN:
                whereBuilder.in(field, splitMultiValues(value));
                break;
            case NOT_IN:
                whereBuilder.notIn(field, splitMultiValues(value));
                break;
            case BETWEEN: {
                String[] values = value.split(",");
                if (values.length != 2) {
                    throw new SearchableParseException(MessageUtils.getMessage("parser.operator.requires.two.values", new Object[]{operatorStr}));
                }
                whereBuilder.between(field, parseValueForBetween(field, values[0], false), parseValueForBetween(field, values[1], true));
                break;
            }
            case NOT_BETWEEN: {
                String[] values = value.split(",");
                if (values.length != 2) {
                    throw new SearchableParseException(MessageUtils.getMessage("parser.operator.requires.two.values", new Object[]{operatorStr}));
                }
                whereBuilder.notBetween(field, parseValueForBetween(field, values[0], false), parseValueForBetween(field, values[1], true));
                break;
            }
            case IS_NULL:
                whereBuilder.isNull(field);
                break;
            case IS_NOT_NULL:
                whereBuilder.isNotNull(field);
                break;
            default:
                throw new SearchableParseException(MessageUtils.getMessage("parser.operator.invalid", new Object[]{operatorStr}));
        }
    }

    /**
     * Splits a comma-separated value into a list. Only multi-value operators (IN/NOT_IN) call this;
     * single-value operators keep the raw value intact so that a literal comma is preserved.
     */
    private List<String> splitMultiValues(String value) {
        return Arrays.asList(value.split(","));
    }

    /**
     * Rejects an empty pattern-matching value so it does not become a match-everything {@code LIKE '%%'}.
     */
    private String requirePatternValue(String operatorStr, String value) {
        if (!StringUtils.hasText(value)) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("parser.pattern.empty", new Object[]{operatorStr}));
        }
        return value;
    }

    private Object parseValue(String field, String value) {
        return parseTyped(field, value, null);
    }

    private Object parseValueForBetween(String field, String value, boolean isEndValue) {
        return parseTyped(field, value, isEndValue);
    }

    /**
     * Reads a parameter value in the type of its DTO field. A String field keeps the text exactly
     * as sent, the same as a JSON body does, so no value is rewritten on one path only.
     */
    private Object parseTyped(String field, String value, Boolean rangeEnd) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        Class<?> fieldType = getFieldType(field);
        if (fieldType == String.class) {
            return value;
        }
        try {
            return rangeEnd == null
                    ? SearchableValueParser.parseValue(value, fieldType)
                    : SearchableValueParser.parseValueForBetween(value, fieldType, rangeEnd);
        } catch (SearchableParseException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.field.value.invalid", new Object[]{field, e.getMessage()}), e);
        }
    }

    private Class<?> getFieldType(String field) {
        Field resolved = SearchableFieldUtils.findFieldInHierarchy(dtoClass, field);
        return resolved == null ? String.class : resolved.getType();
    }
}
