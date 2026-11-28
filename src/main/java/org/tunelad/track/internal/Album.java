package org.tunelad.track.internal;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

/** A release by one Artist. */
@Data
@Table("track_albums")
public class Album {

    @Id
    private String id;

    @Version
    private int version;

    private String title;

    /** The Artist's id, set from {@link #artist} when the album is stored. */
    private String artistId;

    /** Not a column: loaded from the artist table, and stored through {@link #artistId}. */
    @Transient
    private Artist artist;

    private String coverUrl;
    private LocalDate releaseDate;
}
