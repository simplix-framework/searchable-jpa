package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ParameterGenerator {
    private static final String PAGE_PARAM = "page";
    private static final String SIZE_PARAM = "size";
    private static final String SORT_PARAM = "sort";

    private final ParameterSchemaGenerator schemaGenerator;

    public ParameterGenerator() {
        this.schemaGenerator = new ParameterSchemaGenerator();
    }

    public void customizeParameters(Operation operation, Class<?> dtoClass) {
        if (operation.getParameters() == null) {
            operation.setParameters(new ArrayList<>());
        }

        // Add search field parameters
        addSearchFieldParameters(operation, dtoClass);

        // Add the keyword parameter when the DTO opts fields into it
        addKeywordParameter(operation, dtoClass);

        // Add pagination parameters
        addPaginationParameters(operation);

        // Add sorting parameters
        addSortingParameters(operation, dtoClass);
    }

    private void addSearchFieldParameters(Operation operation, Class<?> dtoClass) {
        for (Field field : SearchableFieldUtils.getSearchableFields(dtoClass)) {
            SearchableField searchableField = field.getAnnotation(SearchableField.class);

            String fieldName = field.getName();
            String fieldDescription = getFieldDescription(field);

            for (SearchOperator operator : SearchableFieldUtils.getAllowedOperators(searchableField)) {
                String paramName = fieldName + "." + operator.getName();

                Schema<?> schema = schemaGenerator.createFieldParameterSchema(field, operator);

                add(operation, new Parameter()
                        .name(paramName)
                        .in("query")
                        .description(getOperatorDescription(fieldDescription, operator))
                        .schema(schema)
                        .required(false));
            }
        }
    }

    private void addKeywordParameter(Operation operation, Class<?> dtoClass) {
        List<String> keywordFields = SearchableFieldUtils.getSearchableFields(dtoClass).stream()
                .filter(field -> field.getAnnotation(SearchableField.class).keyword())
                .map(Field::getName)
                .collect(Collectors.toList());

        if (!keywordFields.isEmpty()) {
            add(operation, new Parameter()
                    .name(SearchableParamsParser.KEYWORD_PARAM)
                    .in("query")
                    .description("Matches a record when any of these fields contains the text: " +
                            String.join(", ", keywordFields))
                    .schema(new StringSchema())
                    .required(false));
        }
    }

    private void addPaginationParameters(Operation operation) {
        add(operation, new Parameter()
                .name(PAGE_PARAM)
                .in("query")
                .description("Page number (0-based)")
                .schema(schemaGenerator.createPageSchema())
                .example(0)
                .required(false));

        add(operation, new Parameter()
                .name(SIZE_PARAM)
                .in("query")
                .description("Items per page")
                .schema(schemaGenerator.createSizeSchema())
                .example(20)
                .required(false));
    }

    private void addSortingParameters(Operation operation, Class<?> dtoClass) {
        List<String> sortableFields = SearchableFieldUtils.getSearchableFields(dtoClass).stream()
                .filter(field -> field.getAnnotation(SearchableField.class).sortable())
                .map(Field::getName)
                .collect(Collectors.toList());

        if (!sortableFields.isEmpty()) {
            // One comma-separated value (sort=a.asc,b.desc), which is the form the parser reads.
            add(operation, new Parameter()
                    .name(SORT_PARAM)
                    .in("query")
                    .description("Sort fields, comma-separated (e.g., field.asc,other.desc). Available fields: " +
                            String.join(", ", sortableFields))
                    .schema(schemaGenerator.createSortSchema())
                    .style(Parameter.StyleEnum.FORM)
                    .explode(false)
                    .required(false));
        }
    }

    /**
     * Adds a parameter unless the operation already documents one of that name, so customizing an
     * operation twice leaves one entry per parameter.
     */
    private static void add(Operation operation, Parameter parameter) {
        boolean documented = operation.getParameters().stream()
                .anyMatch(existing -> parameter.getName().equals(existing.getName()));
        if (!documented) {
            operation.getParameters().add(parameter);
        }
    }

    private String getFieldDescription(Field field) {
        return OpenApiDocUtils.getFieldDescription(field);
    }

    private String getOperatorDescription(String fieldDescription, SearchOperator operator) {
        return String.format("%s - %s", fieldDescription, OpenApiDocUtils.getOperationDescription(operator));
    }
} 