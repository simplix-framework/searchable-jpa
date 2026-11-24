package dev.simplecore.searchable.test.fixture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test Data Manager
 * 
 * Handles different database environments:
 * - H2: Creates tables once per test session, loads small dataset
 * - Others: Creates tables if not exist, loads small dataset if tables are empty, preserves data between tests
 */
@Slf4j
@Component
public class TestDataManager {
    
    private static final String SMALL_DATASET_PATH = "test-data/small";
    private static final String[] TABLE_NAMES = {"test_author", "test_post", "test_comment", "test_tag", "test_post_tag"};
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private DataSource dataSource;
    

    
    /**
     * Initialize test data based on database type
     */
    @Transactional
    public void initializeTestData() {
        try {
            DatabaseType dbType = detectDatabaseType();
            log.info("Detected database type: {}", dbType);
            
            if (dbType == DatabaseType.H2) {
                initializeH2Data();
            } else {
                initializeNonH2Data();
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize test data", e);
            throw new RuntimeException("Test data initialization failed", e);
        }
    }
    
    /**
     * H2 database initialization - once per test session
     */
    private void initializeH2Data() {
        if (!isDataEmpty()) {
            log.info("H2 test data already initialized, skipping...");
            return;
        }
        
        log.info("Initializing H2 test data...");
        
        // H2 auto-creates tables via JPA, just load data
        loadSmallDataset();
        restartIdentitiesAfterLoadedKeys();
        
        log.info("H2 test data initialization complete");
    }
    
    /**
     * Moves every identity column past the keys the CSV files brought in. H2 does not advance an
     * identity for a row inserted with its key given, so the next generated key would be 1 again
     * and collide with a loaded row. SQL Server advances it by itself.
     */
    private void restartIdentitiesAfterLoadedKeys() {
        jdbcTemplate.execute((Connection connection) -> {
            for (String table : TABLE_NAMES) {
                String keyColumn = null;
                try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
                    while (columns.next()) {
                        if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                                && "YES".equals(columns.getString("IS_AUTOINCREMENT"))) {
                            keyColumn = columns.getString("COLUMN_NAME");
                        }
                    }
                }
                if (keyColumn == null) {
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    long next;
                    try (ResultSet highest = statement.executeQuery("SELECT COALESCE(MAX(" + keyColumn + "), 0) + 1 FROM " + table)) {
                        highest.next();
                        next = highest.getLong(1);
                    }
                    statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + keyColumn + " RESTART WITH " + next);
                }
            }
            return null;
        });
    }

    /**
     * Non-H2 database initialization - check data only (JPA handles table creation)
     */
    private void initializeNonH2Data() {
        log.info("Initializing non-H2 database...");
        
        // JPA handles table creation via ddl-auto setting
        // Just check if data exists and load if necessary
        if (isDataEmpty()) {
            log.info("Tables are empty, loading minimal test data...");
            loadSmallDataset();
        } else {
            log.info("Data already exists, skipping data load...");
        }
        
        log.info("Non-H2 database initialization complete");
    }
    
    /**
     * Detect database type
     */
    private DatabaseType detectDatabaseType() throws SQLException {
        String databaseProductName;
        try (Connection connection = dataSource.getConnection()) {
            databaseProductName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        }

        if (databaseProductName.contains("h2")) {
            return DatabaseType.H2;
        } else if (databaseProductName.contains("microsoft") || databaseProductName.contains("sql server")) {
            return DatabaseType.MSSQL;
        } else {
            return DatabaseType.OTHER;
        }
    }
    

    
    /**
     * Check if data is empty (check main tables)
     */
    private boolean isDataEmpty() {
        try {
            Integer authorCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_author", Integer.class);
            Integer postCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_post", Integer.class);
            
            log.info("Data check - Authors: {}, Posts: {}", authorCount, postCount);
            
            boolean isEmpty = (authorCount == null || authorCount == 0) && (postCount == null || postCount == 0);
            
            if (!isEmpty) {
                log.info("Existing data found - Authors: {}, Posts: {}. Skipping test data initialization.", 
                    authorCount, postCount);
            }
            
            return isEmpty;
        } catch (Exception e) {
            log.warn("Error checking data existence, assuming empty: {}", e.getMessage());
            return true;
        }
    }
    

    /**
     * Load small dataset from CSV files
     */
    private void loadSmallDataset() {
        log.info("Loading small dataset...");
        
        try {
            // Load in dependency order
            loadCsvData("test_author.csv", "test_author");
            loadCsvData("test_tag.csv", "test_tag");
            loadCsvData("test_post.csv", "test_post");
            loadCsvData("test_comment.csv", "test_comment");
            loadCsvData("test_post_tags.csv", "test_post_tag");
            
            log.info("Small dataset loaded successfully");
        } catch (IOException | SQLException e) {
            log.error("Failed to load small dataset", e);
            throw new RuntimeException("Data loading failed", e);
        }
    }
    
    /**
     * Load CSV data into table using database-specific CSV import
     */
    private void loadCsvData(String csvFileName, String tableName) throws IOException, SQLException {
        ClassPathResource resource = new ClassPathResource(SMALL_DATASET_PATH + "/" + csvFileName);
        
        if (!resource.exists()) {
            throw new IOException("Required test fixture is missing: " + csvFileName);
        }
        
        DatabaseType dbType = detectDatabaseType();
        
        try {
            switch (dbType) {
                            case H2:
                loadCsvDataH2(resource, tableName);
                break;
            case MSSQL:
                loadCsvDataMSSQL(resource, tableName);
                break;
            default:
                // Unknown database type
                log.error("Unknown database type, CSV direct loading is not supported for {}", tableName);
                throw new RuntimeException("CSV direct loading is not supported for unknown database type. Table: " + tableName);
            }
            
            // Count loaded records
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            log.info("Loaded {} records into {}", count != null ? count : 0, tableName);
            
        } catch (Exception e) {
            log.error("Failed to load CSV data for {}: {}", tableName, e.getMessage());
            throw new RuntimeException("CSV loading failed for " + tableName, e);
        }
    }
    
    /**
     * Load CSV data for H2 database
     */
    private void loadCsvDataH2(ClassPathResource resource, String tableName) throws IOException {
        try {
            // First try direct file access
            String csvPath = resource.getFile().getAbsolutePath();
            String sql = CsvInsertStatements.h2InsertFromCsv(tableName, csvPath);
            jdbcTemplate.execute(sql);
            log.info("H2: Loaded CSV data for {} using direct file access", tableName);
            
        } catch (Exception e) {
            log.warn("H2: Direct file access failed, trying classpath resource method: {}", e.getMessage());
            
            try {
                // Try using classpath URL
                String csvUrl = resource.getURL().toString();
                String sql = CsvInsertStatements.h2InsertFromCsv(tableName, csvUrl);
                jdbcTemplate.execute(sql);
                log.info("H2: Loaded CSV data for {} using classpath URL", tableName);
                
            } catch (Exception e2) {
                // If both methods fail, throw exception
                log.error("H2: All CSV loading methods failed for {}", tableName);
                throw new RuntimeException("Failed to load CSV data for " + tableName + " in H2", e2);
            }
        }
    }
    
    /**
     * Load CSV data for MSSQL database
     */
    private void loadCsvDataMSSQL(ClassPathResource resource, String tableName) throws IOException {
        log.info("MSSQL: Loading CSV data for {} using row-by-row INSERT", tableName);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            boolean isFirstLine = true;
            int batchSize = 1000;
            int batchCount = 0;
            List<String> batchStatements = new ArrayList<>();
            
            // Handle IDENTITY_INSERT for tables with auto-increment columns
            boolean needsIdentityInsert = needsIdentityInsert(tableName);
            if (needsIdentityInsert) {
                jdbcTemplate.execute("SET IDENTITY_INSERT " + tableName + " ON");
            }
            
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String insertSql = CsvInsertStatements.insertStatement(tableName, line);
                if (insertSql != null) {
                    batchStatements.add(insertSql);
                    batchCount++;
                    
                    if (batchCount >= batchSize) {
                        executeBatch(batchStatements);
                        batchStatements.clear();
                        batchCount = 0;
                        log.info("MSSQL: Processed {} records for {}", batchSize, tableName);
                    }
                }
            }
            
            // Execute remaining statements
            if (!batchStatements.isEmpty()) {
                executeBatch(batchStatements);
                log.info("MSSQL: Processed final {} records for {}", batchStatements.size(), tableName);
            }
            
            // Turn off IDENTITY_INSERT
            if (needsIdentityInsert) {
                jdbcTemplate.execute("SET IDENTITY_INSERT " + tableName + " OFF");
            }
            
            log.info("MSSQL: Successfully loaded CSV data for {}", tableName);
            
        } catch (Exception e) {
            log.error("MSSQL: Failed to load CSV data for {}: {}", tableName, e.getMessage());
            throw new RuntimeException("Failed to load CSV data for " + tableName + " in MSSQL", e);
        }
    }
    
    /**
     * Check if table needs IDENTITY_INSERT
     */
    private boolean needsIdentityInsert(String tableName) {
        return !tableName.equals("test_post_tag"); // test_post_tag doesn't have auto-increment
    }
    
    /**
     * Execute batch statements
     */
    private void executeBatch(List<String> statements) {
        for (String sql : statements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("MSSQL: Failed to execute statement: {} - Error: {}", sql, e.getMessage());
            }
        }
    }
    
    /**
     * Database type enumeration
     */
    private enum DatabaseType {
        H2, MSSQL, OTHER
    }
} 