package dev.simplecore.searchable.core.annotation;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields as searchable and sortable in DTO classes.
 * This annotation is used to define how a field can be used in search conditions and sorting operations.
 *
 * <p>Example usage:
 * <pre>
 * public class UserDTO {
 *     {@literal @}SearchableField(entityField = "firstName", operators = {EQUALS, CONTAINS}, sortable = true)
 *     private String name;
 *
 *     {@literal @}SearchableField(operators = {GREATER_THAN, LESS_THAN})
 *     private Integer age;
 *
 *     {@literal @}SearchableField(sortable = true, sortField = "profile.displayName")
 *     private String displayName;
 * }
 * </pre>
 *
 * @see SearchOperator
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableField {

    /**
     * Specifies the corresponding field name in the entity class.
     * This is useful when the DTO field name differs from the entity field name,
     * or when mapping to a nested entity field using dot notation.
     *
     * <p>Example:
     * <pre>
     * // Maps to user.address.city in the entity
     * {@literal @}SearchableField(entityField = "address.city")
     * private String city;
     * </pre>
     *
     * @return the entity field name, or empty string to use the annotated field name
     */
    String entityField() default "";

    /**
     * Defines which search operators are allowed for this field.
     * If empty, all operators are allowed.
     *
     * <p>Example:
     * <pre>
     * // Only allows EQUALS and CONTAINS operators
     * {@literal @}SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.CONTAINS})
     * private String name;
     * </pre>
     *
     * @return array of allowed SearchOperators
     */
    SearchOperator[] operators() default {};

    /**
     * Indicates whether this field can be used for sorting operations.
     * When true, the field can be used in sort conditions.
     *
     * <p>Example:
     * <pre>
     * // Field can be used for both searching and sorting
     * {@literal @}SearchableField(sortable = true)
     * private LocalDateTime createdAt;
     * </pre>
     *
     * @return true if the field is sortable, false otherwise
     */
    boolean sortable() default false;

    /**
     * Specifies the field name to use for sorting operations.
     * This is useful when the field used for sorting differs from the field used for searching.
     * When specified, this field will be used for sorting instead of the entityField or the annotated field name.
     *
     * <p>Example:
     * <pre>
     * // Uses "profile.displayName" for sorting even though search uses "name"
     * {@literal @}SearchableField(entityField = "name", sortable = true, sortField = "profile.displayName")
     * private String displayName;
     * </pre>
     *
     * @return the field name to use for sorting, or empty string to use the default field
     */
    String sortField() default "";

    /**
     * Includes this field in the keyword search: the reserved {@code keyword} parameter matches a
     * record when any keyword field contains the text. Only {@code String} fields may opt in, and
     * the field's {@link #operators()} must allow {@code CONTAINS} (or be empty).
     *
     * <p>Example:
     * <pre>
     * // keyword=kim matches a username or a display name containing "kim"
     * {@literal @}SearchableField(operators = {SearchOperator.CONTAINS}, keyword = true)
     * private String username;
     * </pre>
     *
     * @return true if the keyword search matches against this field
     */
    boolean keyword() default false;

    /**
     * Whether text comparisons on this field ignore case. Applies to {@code String} fields.
     *
     * <p>Example:
     * <pre>
     * // code.equals=ab-12 also finds AB-12
     * {@literal @}SearchableField(operators = {SearchOperator.EQUALS}, caseSensitivity = CaseSensitivity.INSENSITIVE)
     * private String code;
     * </pre>
     *
     * @return how case is treated for this field
     */
    CaseSensitivity caseSensitivity() default CaseSensitivity.DEFAULT;

    /**
     * How a condition on this field is matched when its {@link #entityField()} runs through a
     * collection. Has no effect on a field that is not reached through a collection.
     *
     * <p>Example:
     * <pre>
     * // withoutTag.equals=draft finds the posts none of whose tags is named "draft"
     * {@literal @}SearchableField(entityField = "tags.name", operators = {SearchOperator.EQUALS}, match = CollectionMatch.NONE)
     * private String withoutTag;
     * </pre>
     *
     * @return how elements of the collection are matched
     */
    CollectionMatch match() default CollectionMatch.JOINED;

    /**
     * Whether this field may be grouped by and aggregated through the DTO-aware aggregate entry
     * points. Counting per value returns the values themselves, so a field is opted in explicitly.
     *
     * @return true if the field may be used as a group key or an aggregate target
     */
    boolean aggregatable() default false;
}
