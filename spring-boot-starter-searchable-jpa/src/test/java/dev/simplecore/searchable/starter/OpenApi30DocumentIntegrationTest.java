package dev.simplecore.searchable.starter;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = StarterTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:starter-openapi-30;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "springdoc.api-docs.version=openapi_3_0"})
class OpenApi30DocumentIntegrationTest extends AbstractOpenApiDocumentTest {

    @Override
    String expectedVersion() {
        return "3.0";
    }
}
