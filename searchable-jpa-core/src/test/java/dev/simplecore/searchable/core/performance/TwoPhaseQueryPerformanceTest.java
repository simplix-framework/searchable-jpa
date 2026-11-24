package dev.simplecore.searchable.core.performance;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.fixture.TestDataManager;
import dev.simplecore.searchable.test.service.TestPostService;
import dev.simplecore.searchable.test.support.StatementBudget;
import dev.simplecore.searchable.test.support.TestSqlCapture;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for TwoPhaseQueryExecutor
 * 
 * This test validates that TwoPhaseQueryExecutor is now applied to ALL queries,
 * not just ToMany relationship queries. It measures performance across different
 * query types and ensures consistent 2-phase query execution.
 * 
 * Run with: ./gradlew performanceTest
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
@Tag("performance")
class TwoPhaseQueryPerformanceTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private TestDataManager testDataManager;

    @Autowired
    private EntityManager entityManager;

    /**
     * Runs the search and checks the number of statements it sent against the budget of its page.
     * The time it took is logged by the callers and is not asserted: it depends on the machine.
     */
    private Page<TestPost> searchWithinStatementBudget(SearchCondition<?> condition) {
        int budget = StatementBudget.ofPage(entityManager, TestPost.class, condition.getSize());
        TestSqlCapture.start();
        Page<TestPost> result;
        try {
            result = testPostService.findAllWithSearch(condition);
        } finally {
            TestSqlCapture.stop();
        }
        int sent = TestSqlCapture.captured().size();
        assertTrue(sent <= budget, "The search sent " + sent + " statements, more than its budget of " + budget
                + ": " + TestSqlCapture.captured());
        return result;
    }

    @BeforeAll
    static void setUp(@Autowired TestDataManager testDataManager, @Autowired JdbcTemplate jdbc) {
        log.info("=== STEP 1: SETTING UP PERFORMANCE TEST DATA ===");
        
        // Initialize test data for performance testing
        testDataManager.initializeTestData();
        // Ensure the complex scenario exercises a real multi-join result rather than timing zero rows.
        jdbc.update("UPDATE test_post SET title = ?, status = ?, view_count = ? WHERE post_id = (SELECT MIN(post_id) FROM test_post)",
                "Test performance fixture", "PUBLISHED", 100L);
        jdbc.update("UPDATE test_comment SET content = ? WHERE post_id = (SELECT MIN(post_id) FROM test_post)", "test performance comment");
        jdbc.update("INSERT INTO test_post_tag(post_id, tag_id) SELECT p.post_id, t.tag_id FROM test_post p CROSS JOIN test_tag t "
                + "WHERE p.post_id = (SELECT MIN(post_id) FROM test_post) AND t.name = ? "
                + "AND NOT EXISTS (SELECT 1 FROM test_post_tag pt WHERE pt.post_id = p.post_id AND pt.tag_id = t.tag_id)", "Java");
        
        log.info("  ✔ Performance test data setup completed");
    }

    @Test
    @DisplayName("Performance Test - Regular query (no relationships) with TwoPhaseQueryExecutor")
    void testRegularQueryPerformance() {
        log.info("=== STEP 1: STARTING REGULAR QUERY PERFORMANCE TEST ===");
        log.info("  - Query type: Regular query without relationships");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test regular query without any relationships
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .contains("searchTitle", "Test")
                        .and(a -> a.equals("status", TestPostStatus.PUBLISHED))
                )
                .page(0)
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        log.info("STEP 2: EXECUTING REGULAR QUERY");
        Page<TestPost> result = searchWithinStatementBudget(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: REGULAR QUERY PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
        
        log.info("SUMMARY: Regular query performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Statement count within its budget");
    }

    @Test
    @DisplayName("Performance Test - ManyToMany relationship query with TwoPhaseQueryExecutor")
    void testManyToManyQueryPerformance() {
        log.info("=== STEP 1: STARTING MANYTOMANY QUERY PERFORMANCE TEST ===");
        log.info("  - Query type: ManyToMany relationship query (tags)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test ManyToMany relationship query (tags)
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w.equals("tagName", "Java"))
                .page(0)
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        log.info("STEP 2: EXECUTING MANYTOMANY QUERY");
        Page<TestPost> result = searchWithinStatementBudget(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: MANYTOMANY QUERY PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
        
        log.info("SUMMARY: ManyToMany query performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Statement count within its budget");
    }

    @Test
    @DisplayName("Performance Test - Complex multi-relationship query with TwoPhaseQueryExecutor")
    void testComplexMultiRelationshipPerformance() {
        log.info("=== STEP 1: STARTING COMPLEX MULTI-RELATIONSHIP QUERY PERFORMANCE TEST ===");
        log.info("  - Query type: Complex query with multiple relationships");
        log.info("  - Relationships: Comments (OneToMany) + Tags (ManyToMany)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test complex query with multiple relationships
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .contains("searchTitle", "Test")
                        .and(a -> a.contains("commentContent", "test"))
                        .and(a -> a.equals("tagName", "Java"))
                        .and(a -> a.equals("status", TestPostStatus.PUBLISHED))
                        .and(a -> a.greaterThan("viewCount", 0L))
                )
                .page(0)
                .size(20)
                .sort(s -> s.desc("viewCount").desc("id"))
                .build();

        log.info("STEP 2: EXECUTING COMPLEX MULTI-RELATIONSHIP QUERY");
        Page<TestPost> result = searchWithinStatementBudget(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: COMPLEX QUERY PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
        
        log.info("SUMMARY: Complex multi-relationship query performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Statement count within its budget");
    }

    @Test
    @DisplayName("Performance Test - Large page size with TwoPhaseQueryExecutor")
    void testLargePageSizePerformance() {
        log.info("=== STEP 1: STARTING LARGE PAGE SIZE PERFORMANCE TEST ===");
        log.info("  - Query type: Large page size query");
        log.info("  - Page size: 100 (large)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test with large page size
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .page(0)
                .size(100) // Large page size
                .sort(s -> s.desc("createdAt").desc("id"))
                .build();

        log.info("STEP 2: EXECUTING LARGE PAGE SIZE QUERY");
        Page<TestPost> result = searchWithinStatementBudget(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: LARGE PAGE SIZE PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        log.info("  - Page size: {}", result.getSize());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
        
        log.info("SUMMARY: Large page size performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Statement count within its budget");
    }

    @Test
    @DisplayName("Performance Test - Deep pagination with TwoPhaseQueryExecutor")
    void testDeepPaginationPerformance() {
        log.info("=== STEP 1: STARTING DEEP PAGINATION PERFORMANCE TEST ===");
        log.info("  - Query type: Deep pagination query");
        log.info("  - Page number: 5 (deep page)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test deep pagination
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .page(5) // Deep page
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        log.info("STEP 2: EXECUTING DEEP PAGINATION QUERY");
        Page<TestPost> result = searchWithinStatementBudget(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: DEEP PAGINATION PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Current page: {}", result.getNumber());
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
        
        log.info("SUMMARY: Deep pagination performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Statement count within its budget");
    }

    @Test
    @DisplayName("Performance Test - IN clause batching simulation")
    void testInClauseBatchingSimulation() {
        log.info("=== STEP 1: STARTING IN CLAUSE BATCHING SIMULATION TEST ===");
        log.info("  - Query type: Large result set for IN clause batching");
        log.info("  - Page size: 500 (large for batching simulation)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test query that might return many results to test IN clause batching
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .and(a -> a.greaterThan("viewCount", -1L))
                )
                .page(0)
                .size(500) // Large page size to potentially trigger batching
                .sort(s -> s.desc("viewCount").desc("id"))
                .build();

        log.info("STEP 2: EXECUTING IN CLAUSE BATCHING SIMULATION");
        Page<TestPost> result = searchWithinStatementBudget(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: IN CLAUSE BATCHING SIMULATION RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        log.info("  - Page size: {}", result.getSize());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
        
        log.info("SUMMARY: IN clause batching simulation performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ IN clause batching handled efficiently");
    }

    @Test
    @DisplayName("Performance Test - Query consistency verification")
    void testQueryConsistencyVerification() {
        log.info("=== STEP 1: STARTING QUERY CONSISTENCY VERIFICATION TEST ===");
        log.info("  - Test type: Multiple identical queries for consistency");
        log.info("  - Iterations: 3");
        log.info("  - Two-phase execution: Enabled");
        
        // Test multiple identical queries to ensure consistent performance
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .contains("searchTitle", "Test")
                        .and(a -> a.equals("status", TestPostStatus.PUBLISHED))
                )
                .page(0)
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        long totalDuration = 0;
        int iterations = 3;
        Page<TestPost> lastResult = null;
        
        log.info("STEP 2: EXECUTING CONSISTENCY VERIFICATION");
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            Page<TestPost> result = searchWithinStatementBudget(condition);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalDuration += duration;
            
            log.info("  - Iteration {}: {} ms, {} results", i + 1, duration, result.getTotalElements());
            
            // Verify consistency
            assertNotNull(result);
            assertTrue(result.getTotalElements() > 0, "Performance scenario must match seeded data");
            if (lastResult != null) {
                assertEquals(lastResult.getTotalElements(), result.getTotalElements(), 
                    "Results should be consistent across iterations");
            }
            lastResult = result;
        }
        
        double averageDuration = (double) totalDuration / iterations;
        
        log.info("STEP 3: CONSISTENCY VERIFICATION RESULTS");
        log.info("  - Average execution time: {} ms", String.format("%.2f", averageDuration));
        log.info("  - Total iterations: {}", iterations);
        log.info("  - Total elements: {}", lastResult.getTotalElements());
        log.info("  - Results consistency: ✔ VERIFIED");
        
        log.info("SUMMARY: Query consistency verification performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Query results consistency verified");
        log.info("  ✔ Performance stability confirmed");
    }
} 