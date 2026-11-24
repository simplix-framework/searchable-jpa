package dev.simplecore.searchable.core.annotation;

/**
 * How a condition on a field reached through a collection is matched.
 */
public enum CollectionMatch {

    /**
     * Conditions on the same collection are checked against one element together: a record matches
     * when a single element satisfies all of them.
     */
    JOINED,

    /** A record matches when at least one element satisfies this condition, on its own. */
    ANY,

    /** A record matches when no element satisfies this condition. */
    NONE
}
