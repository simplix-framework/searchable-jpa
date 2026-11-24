package dev.simplecore.searchable.core.service.aggregate;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.specification.MatchingRows;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata.SearchField;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.Attribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Counts and sums rows under the same conditions the list beside them is read with.
 *
 * <p>A card above a list ("Failed: 12", "Balance held: 3,400") has to agree with the list under
 * it, so narrowing the list to one customer narrows the card too. The figures are therefore built
 * from the list's own {@link SearchCondition} and computed in the database, like
 * {@link dev.simplecore.searchable.core.service.bucket.TimeBucketCounter}.
 */
public class SearchAggregator {

    /** What an aggregate computes over one field. */
    public enum Op {
        SUM("sum"), COUNT("count"), COUNT_DISTINCT("countDistinct"), MIN("min"), MAX("max"), AVG("avg");

        private final String wireName;

        Op(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @param name {@code sum}, {@code avg}, {@code count}, {@code countDistinct}, {@code min} or
         *             {@code max}
         * @return the operation
         * @throws SearchableValidationException for any other name
         */
        public static Op parse(String name) {
            for (Op op : values()) {
                if (op.wireName.equals(name)) {
                    return op;
                }
            }
            throw new SearchableValidationException(
                    MessageUtils.getMessage("aggregate.operation.unknown", new Object[]{name}));
        }

        /** @return the name {@link #parse} reads */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * One figure to compute.
     *
     * @param field the entity field
     * @param op what to compute over it
     */
    public record Aggregate(String field, Op op) {
    }

    /**
     * One computed figure.
     *
     * @param field the entity field
     * @param op the operation's wire name
     * @param value the figure when it is a number, otherwise null; a sum or count of no rows is 0,
     *              an average, min or max of no rows is null
     * @param result the figure in its own type: the same number as {@code value}, or the smallest
     *               or largest value of a field that is not numeric, such as a date
     */
    public record AggregateValue(String field, String op, Number value, Object result) {

        /**
         * @param field the entity field
         * @param op the operation's wire name
         * @param value the numeric figure
         */
        public AggregateValue(String field, String op, Number value) {
            this(field, op, value, value);
        }
    }

    /**
     * How many rows hold one value.
     *
     * @param value the field's value
     * @param count the rows holding it
     */
    public record ValueCount(Object value, long count) {
    }

    private final EntityManager entityManager;

    /**
     * @param entityManager where the query is run
     */
    public SearchAggregator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Counts matching rows per value of one field.
     *
     * @param entityType the entity being counted
     * @param repository the entity's repository
     * @param condition what the list is currently filtered by
     * @param field the entity field to group by
     * @param <T> the entity type
     * @return one count per value
     * @throws SearchableValidationException when the field is not a single-valued attribute of the entity
     * @see #countByValue(Class, SearchCondition, Specification, String)
     */
    public <T> List<ValueCount> countByValue(Class<T> entityType, JpaSpecificationExecutor<T> repository,
                                             SearchCondition<?> condition, String field) {
        return countByValue(entityType, condition, null, field);
    }

    /**
     * Counts matching rows per value of a field a search DTO offers for aggregation.
     *
     * @param entityType the entity being counted
     * @param condition what the list is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param dtoClass the search DTO the client's field names belong to
     * @param field a field of the DTO marked {@code @SearchableField(aggregatable = true)}
     * @param <T> the entity type
     * @return one count per value
     * @throws SearchableValidationException when the DTO does not offer the field for aggregation
     * @see #countByValue(Class, SearchCondition, Specification, String)
     */
    public <T> List<ValueCount> countByValue(Class<T> entityType, SearchCondition<?> condition,
                                             Specification<T> scope, Class<?> dtoClass, String field) {
        return countByValue(entityType, condition, scope, aggregatableEntityField(dtoClass, field));
    }

    /**
     * Counts matching rows per value of one field.
     *
     * <p>An enum field yields one entry per constant in declaration order, zero for a constant no
     * row holds; a boolean yields {@code true} then {@code false}. Any other field yields only the
     * values present, most frequent first, and no more of them than
     * {@link SearchableJpaSettings#MAX_AGGREGATE_GROUPS} allows. A row whose field is null is not
     * counted.
     *
     * @param entityType the entity being counted
     * @param condition what the list is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param field the entity field to group by
     * @param <T> the entity type
     * @return one count per value
     * @throws SearchableValidationException when the field is not a single-valued attribute of the entity
     */
    public <T> List<ValueCount> countByValue(Class<T> entityType, SearchCondition<?> condition,
                                             Specification<T> scope, String field) {
        requireSingleValued(entityType, field);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        Path<Object> value = root.get(field);
        Expression<Long> total = cb.count(root);
        query.multiselect(value.alias("value"), total.alias("total"))
                .where(cb.and(cb.isNotNull(value), matching(cb, query, root, entityType, condition, scope)))
                .groupBy(value)
                .orderBy(cb.desc(total));

        int mostGroups = SearchableJpaSettings.positiveInt(entityManager,
                SearchableJpaSettings.MAX_AGGREGATE_GROUPS, SearchableJpaSettings.DEFAULT_MAX_AGGREGATE_GROUPS);
        Map<Object, Long> counted = new LinkedHashMap<>();
        for (Tuple row : entityManager.createQuery(query).setMaxResults(mostGroups).getResultList()) {
            counted.put(row.get("value"), row.get("total", Number.class).longValue());
        }

        Class<?> type = value.getJavaType();
        List<Object> order = type.isEnum()
                ? Arrays.asList(type.getEnumConstants())
                : type == Boolean.class || type == boolean.class
                ? List.of(Boolean.TRUE, Boolean.FALSE)
                : new ArrayList<>(counted.keySet());
        return order.stream().map(v -> new ValueCount(v, counted.getOrDefault(v, 0L))).toList();
    }

    /**
     * Computes each requested figure over the matching rows, in one query.
     *
     * @param entityType the entity being aggregated
     * @param repository the entity's repository
     * @param condition what the list is currently filtered by
     * @param aggregates the figures, in the order they are returned
     * @param <T> the entity type
     * @return one value per requested figure
     * @throws SearchableValidationException when a field is not a single-valued attribute of the
     *         entity, or an operation cannot be applied to its field's type
     * @see #aggregate(Class, SearchCondition, Specification, List)
     */
    public <T> List<AggregateValue> aggregate(Class<T> entityType, JpaSpecificationExecutor<T> repository,
                                              SearchCondition<?> condition, List<Aggregate> aggregates) {
        return aggregate(entityType, condition, null, aggregates);
    }

    /**
     * Computes figures over fields a search DTO offers for aggregation, in one query.
     *
     * @param entityType the entity being aggregated
     * @param condition what the list is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param dtoClass the search DTO the client's field names belong to
     * @param aggregates the figures over fields of the DTO marked
     *                   {@code @SearchableField(aggregatable = true)}, in the order they are returned
     * @param <T> the entity type
     * @return one value per requested figure, named by the DTO's field
     * @throws SearchableValidationException when the DTO does not offer a field for aggregation
     * @see #aggregate(Class, SearchCondition, Specification, List)
     */
    public <T> List<AggregateValue> aggregate(Class<T> entityType, SearchCondition<?> condition,
                                              Specification<T> scope, Class<?> dtoClass, List<Aggregate> aggregates) {
        List<Aggregate> onEntity = new ArrayList<>(aggregates.size());
        for (Aggregate aggregate : aggregates) {
            onEntity.add(new Aggregate(aggregatableEntityField(dtoClass, aggregate.field()), aggregate.op()));
        }
        List<AggregateValue> computed = aggregate(entityType, condition, scope, onEntity);
        List<AggregateValue> named = new ArrayList<>(computed.size());
        for (int i = 0; i < computed.size(); i++) {
            AggregateValue value = computed.get(i);
            named.add(new AggregateValue(aggregates.get(i).field(), value.op(), value.value(), value.result()));
        }
        return named;
    }

    /**
     * Computes each requested figure over the matching rows, in one query.
     *
     * @param entityType the entity being aggregated
     * @param condition what the list is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param aggregates the figures, in the order they are returned
     * @param <T> the entity type
     * @return one value per requested figure
     * @throws SearchableValidationException when a field is not a single-valued attribute of the
     *         entity, or an operation cannot be applied to its field's type
     */
    public <T> List<AggregateValue> aggregate(Class<T> entityType, SearchCondition<?> condition,
                                              Specification<T> scope, List<Aggregate> aggregates) {
        if (aggregates.isEmpty()) {
            return List.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(entityType);
        List<Selection<?>> selections = new ArrayList<>();
        for (int i = 0; i < aggregates.size(); i++) {
            requireSingleValued(entityType, aggregates.get(i).field());
            selections.add(expression(cb, root, aggregates.get(i)).alias("a" + i));
        }
        query.multiselect(selections).where(matching(cb, query, root, entityType, condition, scope));
        Tuple row = entityManager.createQuery(query).getSingleResult();

        List<AggregateValue> values = new ArrayList<>();
        for (int i = 0; i < aggregates.size(); i++) {
            Aggregate aggregate = aggregates.get(i);
            Object result = row.get("a" + i);
            // A sum over no rows is null in SQL; the card reads it as nothing, which is zero.
            if (result == null && aggregate.op() == Op.SUM) {
                result = 0L;
            }
            values.add(new AggregateValue(aggregate.field(), aggregate.op().wireName(),
                    result instanceof Number number ? number : null, result));
        }
        return values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Expression<?> expression(CriteriaBuilder cb, Root<T> root, Aggregate aggregate) {
        Path<?> path = root.get(aggregate.field());
        Class<?> type = path.getJavaType();
        boolean numeric = Number.class.isAssignableFrom(type);
        return switch (aggregate.op()) {
            case COUNT -> cb.count(path);
            case COUNT_DISTINCT -> cb.countDistinct(path);
            case SUM -> cb.sum(numericPath(path, aggregate, numeric));
            case AVG -> cb.avg(numericPath(path, aggregate, numeric));
            case MIN -> numeric ? cb.min((Expression<Number>) path) : cb.least(comparablePath(path, aggregate));
            case MAX -> numeric ? cb.max((Expression<Number>) path) : cb.greatest(comparablePath(path, aggregate));
        };
    }

    @SuppressWarnings("unchecked")
    private static Expression<Number> numericPath(Path<?> path, Aggregate aggregate, boolean numeric) {
        if (!numeric) {
            throw operationDoesNotApply(aggregate, path);
        }
        return (Expression<Number>) path;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Expression comparablePath(Path<?> path, Aggregate aggregate) {
        if (!Comparable.class.isAssignableFrom(path.getJavaType())) {
            throw operationDoesNotApply(aggregate, path);
        }
        return path;
    }

    private static SearchableValidationException operationDoesNotApply(Aggregate aggregate, Path<?> path) {
        return new SearchableValidationException(MessageUtils.getMessage("aggregate.operation.type",
                new Object[]{aggregate.op().wireName(), aggregate.field(), path.getJavaType().getSimpleName()}));
    }

    /**
     * Refuses a name that is not an attribute of the entity, and a collection, before a query is
     * built, so the caller is told which field is wrong instead of receiving a provider error.
     */
    private <T> void requireSingleValued(Class<T> entityType, String field) {
        Attribute<? super T, ?> attribute;
        try {
            attribute = entityManager.getMetamodel().managedType(entityType).getAttribute(field);
        } catch (IllegalArgumentException e) {
            throw new SearchableValidationException(MessageUtils.getMessage("aggregate.field.unknown",
                    new Object[]{field, entityType.getSimpleName()}), e);
        }
        if (attribute.isCollection()) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("aggregate.field.collection", new Object[]{field}));
        }
    }

    /**
     * The entity field a DTO field stands for, when the DTO offers it for aggregation.
     *
     * @throws SearchableValidationException when the DTO has no such field or does not mark it
     *         {@code aggregatable}
     */
    private static String aggregatableEntityField(Class<?> dtoClass, String field) {
        SearchField declared = SearchableDtoMetadata.of(dtoClass).field(field);
        if (declared == null || !declared.annotation().aggregatable()) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("aggregate.field.not.allowed", new Object[]{field}));
        }
        return declared.entityField();
    }

    private <T> Predicate matching(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                   Class<T> entityType, SearchCondition<?> condition, Specification<T> scope) {
        return MatchingRows.of(cb, query, root, entityType, entityManager, condition, scope)
                .orElseGet(cb::conjunction);
    }
}
