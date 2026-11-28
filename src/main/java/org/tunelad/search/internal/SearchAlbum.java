package org.tunelad.search.internal;

import java.time.LocalDate;

/** An Album as the search read model sees it, kept up to date from Album events. */
public record SearchAlbum(String id, String title, String artist, String coverUrl, LocalDate releaseDate) {
}
