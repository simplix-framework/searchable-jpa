package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.utils.JsonTypeDetector;
import dev.simplecore.searchable.core.utils.SearchablePatternMatchingHolder;
import dev.simplecore.searchable.core.utils.SearchableValueParser;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import lombok.NonNull;
import org.springframework.util.ClassUtils;

/**
 * Turns the nodes of a search condition into predicates over one query root.
 *
 * @param <T> the root entity type
 */
public class PredicateBuilder<T> {
    private final CriteriaBuilder cb;
    private final JoinManager<T> joinManager;
    private final AbstractQuery<?> query;
    private final boolean bracketOpensCharacterClass;

    /**
     * Creates a builder for conditions that need no subquery of their own.
     */
    public PredicateBuilder(@NonNull CriteriaBuilder cb,
                            @NonNull JoinManager<T> joinManager) {
        this(cb, joinManager, null);
    }

    /**
     * @param cb the criteria builder
     * @param joinManager resolves field paths against the root being filtered
     * @param query the query or subquery that root belongs to; a condition matched per element of a
     *              collection ({@link CollectionMatch#ANY}, {@link CollectionMatch#NONE}) attaches
     *              its own subquery to it
     */
    public PredicateBuilder(@NonNull CriteriaBuilder cb,
                            @NonNull JoinManager<T> joinManager,
                            AbstractQuery<?> query) {
        this.cb = cb;
        this.joinManager = joinManager;
        this.query = query;
        this.bracketOpensCharacterClass = DatabaseTraits.bracketOpensCharacterClass(joinManager.getEntityManager());
    }

    public Predicate build(SearchCondition.Node node) {
        if (node == null) {
            return null;
        }
        if (node instanceof SearchCondition.Condition) {
            return buildPredicate((SearchCondition.Condition) node);
        }
        if (node instanceof SearchCondition.Group) {
            List<SearchCondition.Node> children = ((SearchCondition.Group) node).getNodes();
            if (children == null || children.isEmpty()) {
                return null;
            }
            return ConditionTree.fold(children, cb, this::build);
        }
        throw new SearchableOperationException(
                MessageUtils.getMessage("operation.node.unknown", new Object[]{node.getClass().getSimpleName()}));
    }

    private Predicate buildPredicate(SearchCondition.Condition condition) {
        String entityField = condition.getEntityField();
        if (entityField == null || entityField.isEmpty()) {
            entityField = condition.getField();
        }
        if (entityField == null || entityField.isEmpty()) {
            throw new SearchableOperationException(MessageUtils.getMessage("operation.field.required"));
        }
        if (condition.getSearchOperator() == null) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("operation.operator.required", new Object[]{entityField}));
        }

        CollectionMatch match = condition.getMatch();
        if ((match == CollectionMatch.ANY || match == CollectionMatch.NONE)
                && joinManager.traversesCollection(entityField)) {
            return matchedPerElement(condition, entityField, match);
        }
        return predicateOn(joinManager.getJoinPath(entityField), entityField, condition);
    }

    /**
     * Builds a condition that stands on its own against the elements of a collection: some element
     * satisfies it, or none does. The subquery starts from the entity again and is tied to the row
     * being filtered, so it joins nothing into the outer query.
     */
    private Predicate matchedPerElement(SearchCondition.Condition condition, String entityField, CollectionMatch match) {
        if (query == null) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("operation.match.query.required", new Object[]{entityField}));
        }
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<T> element = subquery.from(joinManager.getEntityClass());
        JoinManager<T> elementJoins = new JoinManager<>(joinManager.getEntityManager(), element);
        Predicate holds = predicateOn(elementJoins.getJoinPath(entityField), entityField, condition);
        subquery.select(cb.literal(1)).where(cb.equal(element, joinManager.getRoot()), holds);
        Predicate someElement = cb.exists(subquery);
        return match == CollectionMatch.NONE ? cb.not(someElement) : someElement;
    }

    private Predicate predicateOn(Path<?> path, String entityField, SearchCondition.Condition condition) {
        Class<?> type = path.getJavaType();
        Object value = condition.getValue();
        SearchOperator operator = condition.getSearchOperator();

        switch (operator) {
            case EQUALS:
                return equality(path, type, convertValue(value, type, entityField), condition);
            case NOT_EQUALS:
                return equality(path, type, convertValue(value, type, entityField), condition).not();
            case GREATER_THAN:
                return cb.greaterThan(comparable(path, entityField), comparableValue(value, type, entityField));
            case GREATER_THAN_OR_EQUAL_TO:
                return cb.greaterThanOrEqualTo(comparable(path, entityField), comparableValue(value, type, entityField));
            case LESS_THAN:
                return cb.lessThan(comparable(path, entityField), comparableValue(value, type, entityField));
            case LESS_THAN_OR_EQUAL_TO:
                return cb.lessThanOrEqualTo(comparable(path, entityField), comparableValue(value, type, entityField));
            case BETWEEN:
                return between(path, entityField, convertValue(value, type, entityField),
                        convertRangeEnd(condition.getValue2(), type, entityField));
            case NOT_BETWEEN:
                return between(path, entityField, convertValue(value, type, entityField),
                        convertRangeEnd(condition.getValue2(), type, entityField)).not();
            case CONTAINS:
                return pattern(path, entityField, condition, text -> "%" + text + "%");
            case NOT_CONTAINS:
                return pattern(path, entityField, condition, text -> "%" + text + "%").not();
            case STARTS_WITH:
                return pattern(path, entityField, condition, text -> text + "%");
            case NOT_STARTS_WITH:
                return pattern(path, entityField, condition, text -> text + "%").not();
            case ENDS_WITH:
                return pattern(path, entityField, condition, text -> "%" + text);
            case NOT_ENDS_WITH:
                return pattern(path, entityField, condition, text -> "%" + text).not();
            case IN:
                return membership(path, type, entityField, condition);
            case NOT_IN:
                return membership(path, type, entityField, condition).not();
            case IS_NULL:
                return cb.isNull(path);
            case IS_NOT_NULL:
                return cb.isNotNull(path);
            default:
                throw new SearchableOperationException(
                        MessageUtils.getMessage("operation.operator.unsupported", new Object[]{operator}));
        }
    }

    /**
     * Whether a text comparison ignores case. A condition says so itself when its field declares
     * it; otherwise the pattern operators follow the application-wide setting and the others
     * compare as the database does.
     */
    private static boolean ignoresCase(SearchCondition.Condition condition, boolean patternOperator) {
        Boolean declared = condition.getCaseInsensitive();
        if (declared != null) {
            return declared;
        }
        return patternOperator && SearchablePatternMatchingHolder.isCaseInsensitive();
    }

    private Predicate equality(Path<?> path, Class<?> type, Object value, SearchCondition.Condition condition) {
        if (type == String.class && value instanceof String && ignoresCase(condition, false)) {
            return cb.equal(cb.lower(path.as(String.class)), ((String) value).toLowerCase(Locale.ROOT));
        }
        return cb.equal(path, value);
    }

    private Predicate membership(Path<?> path, Class<?> type, String entityField, SearchCondition.Condition condition) {
        List<Object> values = new ArrayList<>();
        for (Object item : elements(condition.getValue(), condition.getSearchOperator(), entityField)) {
            values.add(convertValue(item, type, entityField));
        }
        if (values.isEmpty()) {
            throw new SearchableOperationException(MessageUtils.getMessage("operation.collection.empty",
                    new Object[]{condition.getSearchOperator(), entityField}));
        }
        if (type == String.class && ignoresCase(condition, false)) {
            List<Object> lowered = new ArrayList<>();
            for (Object item : values) {
                lowered.add(item instanceof String ? ((String) item).toLowerCase(Locale.ROOT) : item);
            }
            return cb.lower(path.as(String.class)).in(lowered);
        }
        return path.in(values);
    }

    private static Collection<?> elements(Object value, SearchOperator operator, String entityField) {
        if (value instanceof Collection) {
            return (Collection<?>) value;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                items.add(Array.get(value, i));
            }
            return items;
        }
        throw new SearchableOperationException(
                MessageUtils.getMessage("operation.collection.required", new Object[]{operator, entityField}));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate between(Path<?> path, String entityField, Object lower, Object upper) {
        Expression<? extends Comparable> comparablePath = comparable(path, entityField);
        if (!(lower instanceof Comparable) || !(upper instanceof Comparable)) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("operation.between.values.invalid", new Object[]{lower, upper}));
        }
        Comparable exclusiveUpper = RangeBounds.exclusiveUpperBound(upper);
        if (exclusiveUpper != null) {
            return cb.and(cb.greaterThanOrEqualTo(comparablePath, (Comparable) lower),
                    cb.lessThan(comparablePath, exclusiveUpper));
        }
        return cb.between(comparablePath, (Comparable) lower, (Comparable) upper);
    }

    private Predicate pattern(Path<?> path, String entityField, SearchCondition.Condition condition,
                              UnaryOperator<String> shape) {
        Object value = condition.getValue();
        if (value == null || value.toString().isEmpty()) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("operation.pattern.value.required", new Object[]{entityField}));
        }
        Expression<String> text = textOf(path, entityField);
        String pattern = shape.apply(LikePattern.escape(value.toString(), bracketOpensCharacterClass));
        if (ignoresCase(condition, true)) {
            text = cb.lower(text);
            pattern = pattern.toLowerCase(Locale.ROOT);
        }
        // The ESCAPE clause makes the escaped wildcards literal on every database: without it the
        // backslash is not an escape character in standard SQL.
        return cb.like(text, pattern, LikePattern.ESCAPE);
    }

    private Expression<String> textOf(Path<?> path, String entityField) {
        if (String.class.equals(path.getJavaType())
                || JsonTypeDetector.isJsonTypedField(joinManager.getEntityManager(), joinManager.getEntityClass(), entityField)) {
            return path.as(String.class);
        }
        throw new SearchableOperationException(
                MessageUtils.getMessage("operation.pattern.type", new Object[]{entityField}));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Expression<Comparable> comparable(Path<?> path, String entityField) {
        Class<?> type = ClassUtils.resolvePrimitiveIfNecessary(path.getJavaType());
        if (!Comparable.class.isAssignableFrom(type)) {
            throw new SearchableOperationException(MessageUtils.getMessage("operation.comparison.type",
                    new Object[]{entityField, type.getSimpleName()}));
        }
        return (Expression<Comparable>) path;
    }

    @SuppressWarnings({"rawtypes"})
    private Comparable comparableValue(Object value, Class<?> type, String entityField) {
        Object converted = convertValue(value, type, entityField);
        if (converted != null && !(converted instanceof Comparable)) {
            throw new SearchableOperationException(MessageUtils.getMessage("operation.value.type",
                    new Object[]{converted.getClass().getSimpleName(), entityField, type.getSimpleName()}));
        }
        return (Comparable) converted;
    }

    /**
     * Converts the upper bound of a range. Text naming a day without a time is widened to the end of
     * that day, on every path a condition arrives by.
     */
    private Object convertRangeEnd(Object value, Class<?> targetType, String entityField) {
        if (value instanceof String && !targetType.isInstance(value)) {
            return SearchableValueParser.parseValueForBetween((String) value, targetType, true);
        }
        return convertValue(value, targetType, entityField);
    }

    /**
     * Brings a value to the type of the field it is compared with: text is parsed, and a number of
     * another numeric type is read as the field's.
     */
    private Object convertValue(Object value, Class<?> targetType, String entityField) {
        Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(targetType);
        if (value == null || boxed.isInstance(value)) {
            return value;
        }
        if (value instanceof String) {
            return SearchableValueParser.parseValue((String) value, targetType);
        }
        if (value instanceof Number && Number.class.isAssignableFrom(boxed)) {
            return SearchableValueParser.parseValue(value.toString(), targetType);
        }
        throw new SearchableOperationException(MessageUtils.getMessage("operation.value.type",
                new Object[]{value.getClass().getSimpleName(), entityField, targetType.getSimpleName()}));
    }
}
