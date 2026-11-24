package dev.simplecore.searchable.web;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.openapi.utils.SearchParameterSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves a controller parameter {@code @SearchableParams SearchCondition<D>} from the query string.
 *
 * <p>The search DTO is the annotation's value, or the type argument of the parameter when the
 * annotation names none. A parameter sent more than once is read as its values listed, so
 * {@code sort=name.asc&sort=age.desc} sorts by both.
 */
public class SearchConditionArgumentResolver implements HandlerMethodArgumentResolver {

    private final boolean ignoreUnknownParameters;

    /**
     * @param ignoreUnknownParameters whether a query parameter that is not a search parameter of the
     *                                DTO is left out instead of being refused
     */
    public SearchConditionArgumentResolver(boolean ignoreUnknownParameters) {
        this.ignoreUnknownParameters = ignoreUnknownParameters;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SearchCondition.class.isAssignableFrom(parameter.getParameterType())
                && SearchParameterSupport.isQueryParameterSearch(parameter);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Class<?> dtoClass = SearchParameterSupport.searchDto(parameter);
        if (dtoClass == null) {
            throw new SearchableConfigurationException(MessageUtils.getMessage("web.search.dto.unresolved",
                    new Object[]{parameter.getExecutable().toGenericString()}));
        }
        Map<String, String> params = new LinkedHashMap<>();
        webRequest.getParameterMap().forEach((name, values) -> params.put(name, String.join(",", values)));
        return new SearchableParamsParser<>(dtoClass, ignoreUnknownParameters).convert(params);
    }
}
