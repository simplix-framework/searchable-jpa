package dev.simplecore.searchable.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the controller parameter that receives a search sent as query parameters, and names the
 * DTO whose {@link SearchableField} declarations decide what may be searched.
 *
 * <p>On a parameter of type {@code SearchCondition<D>} the Spring Boot starter builds the condition
 * from the request's query parameters. On a {@code Map<String, String>} of the raw parameters the
 * annotation only documents the endpoint, and the controller converts the map itself.
 *
 * <pre>
 * {@literal @}GetMapping
 * public Page&lt;Post&gt; search({@literal @}SearchableParams(PostSearchDTO.class) SearchCondition&lt;PostSearchDTO&gt; condition) {
 *     return postService.findAllWithSearch(condition);
 * }
 * </pre>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableParams {

    /**
     * @return the search DTO; may be left out on a {@code SearchCondition<D>} parameter, whose type
     *         argument then names it
     */
    Class<?> value() default Void.class;
}
