package dev.simplecore.searchable.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the message bundles as a whole: both languages define the same keys, every argument a
 * message declares is actually substituted, and the keys and the code agree in both directions.
 */
class MessageBundleTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");
    private static final Pattern KEY_IN_SOURCE = Pattern.compile("\"\\{?([a-z][a-z0-9]*(?:\\.[a-z0-9]+)+)}?\"");

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static Properties bundle(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleTest.class.getResourceAsStream("/messages/" + name)) {
            assertThat(in).as("bundle %s on the classpath", name).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    @DisplayName("both languages define the same keys")
    void bothLanguagesDefineTheSameKeys() throws IOException {
        assertThat(new TreeSet<>(bundle("message_ko.properties").stringPropertyNames()))
                .isEqualTo(new TreeSet<>(bundle("message.properties").stringPropertyNames()));
    }

    @Test
    @DisplayName("every declared argument is substituted in both languages")
    void everyDeclaredArgumentIsSubstituted() throws IOException {
        Properties english = bundle("message.properties");
        List<String> unsubstituted = new ArrayList<>();
        for (Locale locale : List.of(Locale.ENGLISH, Locale.KOREAN)) {
            LocaleContextHolder.setLocale(locale);
            for (String key : english.stringPropertyNames()) {
                Set<Integer> indexes = new TreeSet<>();
                Matcher matcher = PLACEHOLDER.matcher(english.getProperty(key));
                while (matcher.find()) {
                    indexes.add(Integer.parseInt(matcher.group(1)));
                }
                if (indexes.isEmpty()) {
                    continue;
                }
                int highest = indexes.stream().mapToInt(Integer::intValue).max().orElse(0);
                Object[] arguments = new Object[highest + 1];
                for (int i = 0; i <= highest; i++) {
                    arguments[i] = "<arg" + i + ">";
                }
                String formatted = MessageUtils.getMessage(key, arguments);
                for (int index : indexes) {
                    if (!formatted.contains("<arg" + index + ">")) {
                        unsubstituted.add(locale + " " + key + " -> " + formatted);
                    }
                }
            }
        }
        assertThat(unsubstituted).isEmpty();
    }

    @Test
    @DisplayName("every key the code asks for is defined, and every defined key is asked for")
    void keysAndCodeAgree() throws IOException {
        Set<String> defined = new TreeSet<>(bundle("message.properties").stringPropertyNames());
        Set<String> referenced = new TreeSet<>();
        Path modules = repositoryRoot();
        List<Path> sourceRoots = List.of(
                modules.resolve("searchable-jpa-core/src/main/java"),
                modules.resolve("searchable-jpa-openapi/src/main/java"),
                modules.resolve("spring-boot-starter-searchable-jpa/src/main/java"));
        for (Path sourceRoot : sourceRoots) {
            assertThat(sourceRoot).as("source root (run from the module directory)").isDirectory();
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher matcher = KEY_IN_SOURCE.matcher(Files.readString(file));
                    while (matcher.find()) {
                        referenced.add(matcher.group(1));
                    }
                }
            }
        }
        Set<String> undefined = new TreeSet<>(referenced);
        undefined.retainAll(prefixed(referenced, defined));
        undefined.removeAll(defined);
        assertThat(undefined).as("keys used in code but missing from the bundle").isEmpty();

        Set<String> unused = new TreeSet<>(defined);
        unused.removeAll(referenced);
        assertThat(unused).as("keys defined in the bundle but never used in code").isEmpty();
    }

    /**
     * Finds the repository root from where this class was compiled to, so the result does not
     * depend on the directory the test run was started from.
     */
    private static Path repositoryRoot() {
        Path path;
        try {
            path = Path.of(MessageBundleTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) {
            path = path.getParent();
        }
        assertThat(path).as("a parent directory holding settings.gradle").isNotNull();
        return path;
    }

    /**
     * Keeps only the string literals that are message keys: those sharing their first segment with a
     * defined key. Other dotted literals in the code (property names, package names) are not keys.
     */
    private static Set<String> prefixed(Set<String> literals, Set<String> defined) {
        Set<String> firstSegments = new TreeSet<>();
        for (String key : defined) {
            firstSegments.add(key.substring(0, key.indexOf('.')));
        }
        Set<String> keys = new TreeSet<>();
        for (String literal : literals) {
            if (firstSegments.contains(literal.substring(0, literal.indexOf('.')))) {
                keys.add(literal);
            }
        }
        return keys;
    }
}
