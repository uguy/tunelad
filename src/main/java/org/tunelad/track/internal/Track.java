package org.tunelad.track.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import org.tunelad.account.AccountRef;
import org.tunelad.set.SetDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

import java.util.ArrayList;
import java.util.List;

@Data
@Table("track_tracks")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Track {

    @Id
    private String id;

    @Version
    private int version;

    private String title;
    private String description;

    /** The Artist's id, set from {@link #artist} when the track is stored. */
    private String artistId;

    /** Not a column: loaded from the artist table, and stored through {@link #artistId}. */
    @Transient
    private Artist artist;
    private String album;
    private String artUrl;

    @Transient
    private Publisher<DataBuffer> data;

    private TrackFormat format;

    /** The Set this track records, of the set module, by id; null for tracks stored before Sets existed. Not mapped
     * with the other columns (Spring Data takes a property named setId for the setter of id): DbTracks stores it itself. */
    @Transient
    private String setId;

    /** Not a column: the Set read from the set module. */
    @Transient
    private SetDTO tuneSet;

    /** Not a column: stored in track_performances, one per Tune of the Set, in order. */
    @Transient
    private List<Performance> performances = new ArrayList<>();

    @Embedded.Nullable(prefix = "owner_")
    private TrackOwner owner;

    private List<String> tags = new ArrayList<>();

    /** The text fields (title, description, artist, album) an Account provided: tags read from the audio never replace them.
     * Not mapped with the other columns (Spring Data cannot read it back through its generated accessor): DbTracks stores it itself. */
    @Transient
    private List<String> providedTextFields = new ArrayList<>();

    /** Only its owner, or an administrator, may edit or delete a track. */
    public boolean isChangeableBy(AccountRef account) {
        return account.admin() || (owner != null && owner.id().equals(account.id()));
    }
}
