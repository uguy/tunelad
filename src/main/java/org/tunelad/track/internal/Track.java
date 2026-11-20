package org.tunelad.track.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "tracks")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Track {

    @Id
    private String id;

    @Version
    private int version;

    private String title;
    private String description;

    private Artist artist;
    private String album;
    private String artUrl;

    @BsonIgnore
    private Publisher<DataBuffer> data;

    private TrackFormat format;

    private TrackOwner owner;
    @Indexed
    private List<String> tags = new ArrayList<>();
}
