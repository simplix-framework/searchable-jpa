package dev.simplecore.searchable.core.utils;

import java.util.Objects;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Process-wide holder of the {@link ConversionService} that reads a search value into a type the
 * library has no parser of its own for.
 *
 * <p>Spring's shared default service is used until an application supplies its own; the Spring Boot
 * starter supplies the application's conversion service at startup, so a converter registered there
 * makes its target type searchable.
 */
public final class SearchableConversionHolder {

    private static volatile ConversionService conversionService = DefaultConversionService.getSharedInstance();

    private SearchableConversionHolder() {
    }

    /**
     * @param service the conversion service search values are converted with
     */
    public static void setConversionService(ConversionService service) {
        conversionService = Objects.requireNonNull(service, "conversionService must not be null");
    }

    /**
     * @return the conversion service search values are converted with
     */
    public static ConversionService getConversionService() {
        return conversionService;
    }
}
