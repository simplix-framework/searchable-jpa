package dev.simplecore.searchable.openapi.customiser;

import dev.simplecore.searchable.core.annotation.SearchableParams;
import dev.simplecore.searchable.openapi.generator.DescriptionGenerator;
import dev.simplecore.searchable.openapi.generator.ExampleGenerator;
import dev.simplecore.searchable.openapi.generator.ParameterGenerator;
import dev.simplecore.searchable.openapi.utils.SearchParameterSupport;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents a search endpoint from its search DTO.
 *
 * <p>A parameter annotated with {@link SearchableParams} is a search sent as query parameters: the
 * operation gets one parameter per field and operator, and the parameter that only carries the raw
 * values is taken out of the document. A {@code SearchCondition} request body gets an example.
 */
public class OpenApiDocCustomiser implements OperationCustomizer {
    private static final String MEDIA_TYPE_JSON = "application/json";
    private static final String EXAMPLE_NAME = "example";

    private final ExampleGenerator exampleGenerator = new ExampleGenerator();
    private final ParameterGenerator parameterGenerator = new ParameterGenerator();

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            Class<?> dtoClass = SearchParameterSupport.searchDto(parameter);
            if (dtoClass == null) {
                continue;
            }
            DescriptionGenerator.customizeOperation(operation, dtoClass);
            if (SearchParameterSupport.isQueryParameterSearch(parameter)) {
                removeCarrierParameter(operation, parameter);
                parameterGenerator.customizeParameters(operation, dtoClass);
            } else {
                customizeRequestBody(operation, dtoClass);
            }
            break;
        }
        return operation;
    }

    /**
     * Takes the parameter that only carries the raw query values out of the document. SpringDoc
     * lists it as one object-typed query parameter, which is not something a client can send.
     */
    private static void removeCarrierParameter(Operation operation, MethodParameter parameter) {
        if (operation.getParameters() == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        if (parameter.getParameterName() != null) {
            names.add(parameter.getParameterName());
        }
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && StringUtils.hasText(requestParam.name())) {
            names.add(requestParam.name());
        }
        operation.getParameters().removeIf(documented -> names.contains(documented.getName()));
    }

    /**
     * Adds an example to the JSON request body SpringDoc documented. The schema stays the one
     * SpringDoc resolved, whose components it registered in the same document.
     */
    private void customizeRequestBody(Operation operation, Class<?> dtoClass) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null) {
            return;
        }
        MediaType mediaType = requestBody.getContent().get(MEDIA_TYPE_JSON);
        if (mediaType == null) {
            return;
        }
        Example example = new Example();
        example.setValue(exampleGenerator.generateExampleObject(dtoClass));
        example.setSummary("Example");
        Map<String, Example> examples = new LinkedHashMap<>();
        examples.put(EXAMPLE_NAME, example);
        mediaType.examples(examples);
        requestBody.required(true);
    }
}
