package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a search DTO declares, read once per class.
 *
 * <p>A DTO is described by its fields and their {@link SearchableField} annotations alone, so a
 * record, a class with accessors and a class without any are all read the same way. Fields of a
 * subclass come before those it inherits, and hide an inherited field of the same name.
 */
public final class SearchableDtoMetadata {

    /**
     * One searchable field of a DTO.
     *
     * @param field the declaring field
     * @param annotation its declaration
     */
    public record SearchField(Field field, SearchableField annotation) {

        /** @return the name clients use */
        public String name() {
            return field.getName();
        }

        /** @return the type search values are read in */
        public Class<?> type() {
            return field.getType();
        }

        /** @return the entity path conditions on this field are applied to */
        public String entityField() {
            return annotation.entityField().isEmpty() ? field.getName() : annotation.entityField();
        }

        /** @return the entity path sorting by this field uses */
        public String sortField() {
            return annotation.sortField().isEmpty() ? entityField() : annotation.sortField();
        }

        /** @return whether the declaration allows the operator; an empty declaration allows all */
        public boolean allows(SearchOperator operator) {
            return annotation.operators().length == 0 || Arrays.asList(annotation.operators()).contains(operator);
        }

        /** @return whether the field is a map, collection or array, which is stored and matched as text */
        public boolean storedAsText() {
            return Map.class.isAssignableFrom(field.getType()) || Collection.class.isAssignableFrom(field.getType())
                    || field.getType().isArray();
        }
    }

    private static final ClassValue<SearchableDtoMetadata> CACHE = new ClassValue<>() {
        @Override
        protected SearchableDtoMetadata computeValue(Class<?> type) {
            return new SearchableDtoMetadata(type);
        }
    };

    private final Map<String, SearchField> fields;

    private SearchableDtoMetadata(Class<?> dtoClass) {
        Map<String, SearchField> declared = new LinkedHashMap<>();
        for (Class<?> type = dtoClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                SearchableField annotation = field.getAnnotation(SearchableField.class);
                if (annotation != null) {
                    declared.putIfAbsent(field.getName(), new SearchField(field, annotation));
                }
            }
        }
        this.fields = Collections.unmodifiableMap(declared);
    }

    /**
     * @param dtoClass a search DTO
     * @return what it declares
     */
    public static SearchableDtoMetadata of(Class<?> dtoClass) {
        return CACHE.get(dtoClass);
    }

    /**
     * @param name a field name as a client sends it
     * @return the searchable field of that name, or null when the DTO declares none
     */
    public SearchField field(String name) {
        return name == null ? null : fields.get(name);
    }

    /** @return every searchable field, a subclass's before those it inherits */
    public Collection<SearchField> fields() {
        return fields.values();
    }

    /** @return the fields the keyword parameter matches against, in declaration order */
    public List<SearchField> keywordFields() {
        return fields.values().stream().filter(field -> field.annotation().keyword()).toList();
    }
}
