package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableConversionHolder;
import dev.simplecore.searchable.core.utils.SearchablePatternMatchingHolder;
import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.core.convert.ConversionService;

/** What an application wires itself when it uses the core module without the starter. */
public final class CoreOnlySetup {

    private CoreOnlySetup() {
    }

    // docs:start process
    public static void applyProcessSettings(ZoneId zone, ConversionService conversionService,
                                            MessageSource messageSource) {
        SearchableTimeZoneHolder.setZoneId(zone);
        SearchablePatternMatchingHolder.setCaseInsensitive(false);
        SearchableConversionHolder.setConversionService(conversionService);
        SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(1000, 32, 1000, 16));
        MessageUtils.init(messageSource);
    }
    // docs:end process

    // docs:start unit
    public static Map<String, Object> persistenceUnitSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put(SearchableJpaSettings.DEFAULT_PAGE_SIZE, 20);
        settings.put(SearchableJpaSettings.MAX_PAGE_SIZE, 1000);
        settings.put(SearchableJpaSettings.AUTO_FETCH_COLLECTIONS, true);
        settings.put(SearchableJpaSettings.IN_CLAUSE_BATCH_SIZE, 500);
        settings.put(SearchableJpaSettings.WRITE_CHUNK_SIZE, 200);
        settings.put(SearchableJpaSettings.REQUIRE_WRITE_FILTER, false);
        settings.put(SearchableJpaSettings.MAX_AGGREGATE_GROUPS, 1000);
        settings.put(SearchableJpaSettings.MAX_BUCKETS, 512);
        return settings;
    }
    // docs:end unit
}
