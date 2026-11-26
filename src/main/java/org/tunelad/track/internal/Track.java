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

    @Embedded.Nullable(prefix = "artist_")
    private Artist artist;
    private String album;
    private String artUrl;

    @Transient
    private Publisher<DataBuffer> data;

    private TrackFormat format;

    @Embedded.Nullable(prefix = "owner_")
    private TrackOwner owner;

    private List<String> tags = new ArrayList<>();

    /** Only its owner, or an administrator, may edit or delete a track. */
    public boolean isChangeableBy(AccountRef account) {
        return account.admin() || (owner != null && owner.id().equals(account.id()));
    }
}
