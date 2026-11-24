package dev.simplecore.searchable.test.fixture;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * The statements that bring the CSV fixtures into each test table.
 */
@Slf4j
final class CsvInsertStatements {

    private CsvInsertStatements() {
    }

    /**
     * Build H2 CSV insert query with proper column mapping
     */
    static String h2InsertFromCsv(String tableName, String csvPath) {
        switch (tableName) {
            case "test_author":
                return String.format(
                    "INSERT INTO %s (author_id, name, email, nickname, description, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_post":
                return String.format(
                    "INSERT INTO %s (post_id, title, content, status, view_count, like_count, author_id, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_tag":
                return String.format(
                    "INSERT INTO %s (tag_id, name, description, color, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_comment":
                return String.format(
                    "INSERT INTO %s (comment_id, content, author_id, post_id, created_at, updated_at, created_by, updated_by) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            case "test_post_tag":
                return String.format(
                    "INSERT INTO %s (post_id, tag_id) " +
                    "SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
            default:
                // Generic fallback
                return String.format(
                    "INSERT INTO %s SELECT * FROM CSVREAD('%s', NULL, 'charset=UTF-8 fieldSeparator=,')", 
                    tableName, csvPath
                );
        }
    }

    /**
     * Build INSERT statement for MSSQL
     */
    static String insertStatement(String tableName, String csvLine) {
        Object[] values = parseCsvLine(csvLine);
        
        switch (tableName) {
            case "test_author":
                if (values.length >= 9) {
                    return String.format(
                        "INSERT INTO test_author (author_id, name, email, nickname, description, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        escapeSqlString(values[2].toString()),
                        escapeSqlString(values[3].toString()),
                        escapeSqlString(values[4].toString()),
                        values[5], values[6],
                        escapeSqlString(values[7].toString()),
                        escapeSqlString(values[8].toString())
                    );
                }
                break;
            case "test_post":
                if (values.length >= 11) {
                    return String.format(
                        "INSERT INTO test_post (post_id, title, content, status, view_count, like_count, author_id, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', '%s', '%s', %s, %s, %s, '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        escapeSqlString(values[2].toString()),
                        escapeSqlString(values[3].toString()),
                        values[4], values[5], values[6],
                        values[7], values[8],
                        escapeSqlString(values[9].toString()),
                        escapeSqlString(values[10].toString())
                    );
                }
                break;
            case "test_tag":
                if (values.length >= 8) {
                    return String.format(
                        "INSERT INTO test_tag (tag_id, name, description, color, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        escapeSqlString(values[2].toString()),
                        escapeSqlString(values[3].toString()),
                        values[4], values[5],
                        escapeSqlString(values[6].toString()),
                        escapeSqlString(values[7].toString())
                    );
                }
                break;
            case "test_comment":
                if (values.length >= 8) {
                    return String.format(
                        "INSERT INTO test_comment (comment_id, content, author_id, post_id, created_at, updated_at, created_by, updated_by) " +
                        "VALUES (%s, '%s', %s, %s, '%s', '%s', '%s', '%s')",
                        values[0],
                        escapeSqlString(values[1].toString()),
                        values[2], values[3],
                        values[4], values[5],
                        escapeSqlString(values[6].toString()),
                        escapeSqlString(values[7].toString())
                    );
                }
                break;
            case "test_post_tag":
                if (values.length >= 2) {
                    return String.format(
                        "INSERT INTO test_post_tag (post_id, tag_id) VALUES (%s, %s)",
                        values[0], values[1]
                    );
                }
                break;
        }
        
        log.warn("MSSQL: Unable to build INSERT statement for {} with {} values", tableName, values.length);
        return null;
    }

    /**
     * Escape SQL string values and convert \n back to actual newlines
     */
    private static String escapeSqlString(String value) {
        if (value == null) {
            return "";
        }
        // Convert \n string representation back to actual newlines
        value = value.replace("\\n", "\n");
        value = value.replace("\\r", "\r");
        
        return value.replace("'", "''");
    }

    /**
     * Parse CSV line into values
     */
    private static Object[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // Skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        
        return values.toArray();
    }
}
