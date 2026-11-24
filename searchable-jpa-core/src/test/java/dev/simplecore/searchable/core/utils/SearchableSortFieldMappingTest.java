package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

/**
 * The entity field a DTO field is sorted by.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchableSortFieldMappingTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        // Set English locale for tests to ensure consistent error messages
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        // This test context's application zone is the JVM zone (deployment rule 4),
        // so timezone-less values resolve exactly as these assertions expect.
        SearchableTimeZoneHolder.setZoneId(ZoneId.systemDefault());
    }

    @Test
    @DisplayName("getSortFieldFromDto should return sortField when specified")
    void testGetSortFieldFromDto_WithSortField() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "authorName");
        
        // Then
        assertThat(result).isEqualTo("author.name");
    }

    @Test
    @DisplayName("getSortFieldFromDto should return createdAt when sortField is specified")
    void testGetSortFieldFromDto_WithSortFieldCreatedAt() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "createdDate");
        
        // Then
        assertThat(result).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("getSortFieldFromDto should return sortField over entityField when both are specified")
    void testGetSortFieldFromDto_SortFieldOverEntityField() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "lastModified");
        
        // Then
        assertThat(result).isEqualTo("modifiedAt"); // sortField takes priority over entityField
    }

    @Test
    @DisplayName("getSortFieldFromDto should fallback to field name when no sortField or entityField")
    void testGetSortFieldFromDto_FallbackToFieldName() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "title");
        
        // Then
        assertThat(result).isEqualTo("title"); // No sortField or entityField, use field name
    }

    @Test
    @DisplayName("getSortFieldFromDto should return field name when class is null")
    void testGetSortFieldFromDto_NullClass() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(null, "someField");
        
        // Then
        assertThat(result).isEqualTo("someField");
    }

    @Test
    @DisplayName("getSortFieldFromDto should return field name when field doesn't exist")
    void testGetSortFieldFromDto_NonExistentField() {
        // Given
        String result = SearchableFieldUtils.getSortFieldFromDto(TestPostDTOs.TestPostSortFieldDTO.class, "nonExistentField");
        
        // Then
        assertThat(result).isEqualTo("nonExistentField");
    }
}
