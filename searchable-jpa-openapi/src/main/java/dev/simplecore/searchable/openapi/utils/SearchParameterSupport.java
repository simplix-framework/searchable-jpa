package dev.simplecore.searchable.openapi.utils;

import dev.simplecore.searchable.core.annotation.SearchableParams;
import dev.simplecore.searchable.core.condition.SearchCondition;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

/**
 * Reads what a controller method parameter says about the search it carries.
 */
public final class SearchParameterSupport {

    private SearchParameterSupport() {
    }

    /**
     * @param parameter a controller method parameter
     * @return whether the parameter is a search sent as query parameters
     */
    @SuppressWarnings("deprecation")
    public static boolean isQueryParameterSearch(MethodParameter parameter) {
        // The second annotation has the simple name of the first, which replaces it; both are needed here.
        return parameter.hasParameterAnnotation(SearchableParams.class)
                || parameter.hasParameterAnnotation(dev.simplecore.searchable.openapi.annotation.SearchableParams.class);
    }

    /**
     * @param parameter a controller method parameter
     * @return the search DTO the parameter names, by its annotation or by the type argument of a
     *         {@code SearchCondition}; null when the parameter is not a search
     */
    @SuppressWarnings("deprecation")
    public static Class<?> searchDto(MethodParameter parameter) {
        SearchableParams annotation = parameter.getParameterAnnotation(SearchableParams.class);
        if (annotation != null && annotation.value() != Void.class) {
            return annotation.value();
        }
        // The annotation of the same simple name this one replaces; both are needed here.
        dev.simplecore.searchable.openapi.annotation.SearchableParams legacy =
                parameter.getParameterAnnotation(dev.simplecore.searchable.openapi.annotation.SearchableParams.class);
        if (legacy != null) {
            return legacy.value();
        }
        if (!SearchCondition.class.isAssignableFrom(parameter.getParameterType())) {
            return null;
        }
        return ResolvableType.forMethodParameter(parameter).as(SearchCondition.class).getGeneric(0).resolve();
    }
}
