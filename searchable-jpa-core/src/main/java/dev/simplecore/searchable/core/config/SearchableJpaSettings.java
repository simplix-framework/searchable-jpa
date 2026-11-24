package dev.simplecore.searchable.core.config;

import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.EntityManager;

/**
 * Names and readers of the settings the query code takes from the EntityManagerFactory it runs
 * against.
 *
 * <p>Keeping the values on the factory gives each persistence unit its own. The Spring Boot starter
 * fills them from {@code searchable.*}; an application that builds its own factory sets the same
 * keys in its JPA properties.
 */
public final class SearchableJpaSettings {

    /** Largest page size a request may ask for. */
    public static final String MAX_PAGE_SIZE = "searchable.max_page_size";

    /** Page size used when a condition carries none. */
    public static final String DEFAULT_PAGE_SIZE = "searchable.default_page_size";

    /** Whether to-one paths behind a direct collection are fetched without being asked for. */
    public static final String AUTO_FETCH_COLLECTIONS = "searchable.auto_fetch_collections";

    /** How many identifiers one entity-loading statement carries. */
    public static final String IN_CLAUSE_BATCH_SIZE = "searchable.in_clause_batch_size";

    /** How many entities a search-based update or delete handles between flushes. */
    public static final String WRITE_CHUNK_SIZE = "searchable.write_chunk_size";

    /** Whether a search-based update or delete refuses to run without any condition or scope. */
    public static final String REQUIRE_WRITE_FILTER = "searchable.require_write_filter";

    /** How many distinct values a count per value may return. */
    public static final String MAX_AGGREGATE_GROUPS = "searchable.max_aggregate_groups";

    /** How many slices one time-bucket count may ask for. */
    public static final String MAX_BUCKETS = "searchable.max_buckets";

    public static final int DEFAULT_IN_CLAUSE_BATCH_SIZE = 500;
    public static final int DEFAULT_WRITE_CHUNK_SIZE = 200;
    public static final int DEFAULT_MAX_AGGREGATE_GROUPS = 1000;

    private SearchableJpaSettings() {
    }

    /**
     * @param entityManager an entity manager of the persistence unit being queried
     * @param key the setting's key
     * @param defaultValue the value used when the unit does not set the key
     * @return the configured positive integer, or the default
     * @throws SearchableConfigurationException when the configured value is not a positive integer
     */
    public static int positiveInt(EntityManager entityManager, String key, int defaultValue) {
        Object value = entityManager.getEntityManagerFactory().getProperties().get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        int parsed;
        try {
            parsed = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new SearchableConfigurationException(
                    MessageUtils.getMessage("settings.positive.integer.required", new Object[]{key, text}), e);
        }
        if (parsed < 1) {
            throw new SearchableConfigurationException(
                    MessageUtils.getMessage("settings.positive.integer.required", new Object[]{key, text}));
        }
        return parsed;
    }

    /**
     * @param entityManager an entity manager of the persistence unit being queried
     * @param key the setting's key
     * @param defaultValue the value used when the unit does not set the key
     * @return the configured flag, or the default
     */
    public static boolean flag(EntityManager entityManager, String key, boolean defaultValue) {
        Object value = entityManager.getEntityManagerFactory().getProperties().get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString().trim());
    }
}
