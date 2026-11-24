package dev.simplecore.searchable.core.annotation;

/**
 * Whether text comparisons on a searchable field ignore case.
 */
public enum CaseSensitivity {

    /**
     * Pattern operators follow the application-wide pattern-matching setting; equality and
     * membership compare as the database does.
     */
    DEFAULT,

    /** Equality, membership and pattern operators all ignore case on this field. */
    INSENSITIVE,

    /** Every operator compares as the database does, whatever the application-wide setting is. */
    SENSITIVE
}
