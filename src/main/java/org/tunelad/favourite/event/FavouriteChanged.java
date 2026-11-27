package org.tunelad.favourite.event;

/** The favourites of an account changed: the track was marked or unmarked, or deleted. */
public record FavouriteChanged(String accountId, String trackId) {
}
