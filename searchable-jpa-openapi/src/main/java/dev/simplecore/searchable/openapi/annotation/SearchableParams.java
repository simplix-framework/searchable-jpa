package dev.simplecore.searchable.openapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the search DTO of a query-parameter search endpoint.
 *
 * @deprecated use {@link dev.simplecore.searchable.core.annotation.SearchableParams}, which lives in
 *             the core module and so does not require the OpenAPI module. This annotation keeps
 *             being honoured.
 */
@Deprecated
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableParams {
    Class<?> value();
}
