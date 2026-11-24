package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.openapi.customiser.OpenApiDocCustomiser;
import dev.simplecore.searchable.properties.SearchableProperties;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@EnableConfigurationProperties(SearchableProperties.class)
@ConditionalOnProperty(name = "searchable.swagger.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OpenAPI.class, OperationCustomizer.class})
@AutoConfiguration
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
public class SearchableOpenApiConfiguration {
    // Gated on RequestMappingHandlerMapping so the customizer is only registered in a servlet web
    // application once Spring MVC is configured; the mapping itself is not needed by the customizer.
    @Bean(name = "searchConditionCustomizer")
    @ConditionalOnMissingBean(name = "searchConditionCustomizer")
    @ConditionalOnBean(RequestMappingHandlerMapping.class)
    public OperationCustomizer searchConditionCustomizer() {
        return new OpenApiDocCustomiser();
    }
}