package org.tunelad.media;

import org.springframework.lang.Nullable;

/** The tags an audio file carries about itself; each is null when the file has none, or a blank one. */
public record AudioTags(@Nullable String title, @Nullable String artist, @Nullable String album,
                        @Nullable String comment) {

    public static final AudioTags NONE = new AudioTags(null, null, null, null);
}
