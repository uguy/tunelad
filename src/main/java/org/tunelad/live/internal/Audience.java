package org.tunelad.live.internal;

import org.jspecify.annotations.Nullable;

/**
 * Who a live event is for: everyone (the shared track library), or the connections of one account.
 *
 * @param accountKey the account's key, see {@link LiveSubjects#accountKey}; null for everyone
 */
record Audience(@Nullable String accountKey) {

    static final Audience LIBRARY = new Audience(null);

    /** Whether a connection of this viewer (null for an anonymous one) is meant to receive the event. */
    boolean admits(@Nullable String viewerKey) {
        return accountKey == null || accountKey.equals(viewerKey);
    }
}
