package org.tunelad.search;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.tunelad.track.TrackFormat;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

@Data
@Document(indexName = "tracks")
@Schema(title = "IndexedTrack", description = "Track searchable info")
public class IndexedTrack {
	@Id
	private String id;

	@MultiField(mainField = @Field(type = FieldType.Text, fielddata = true), otherFields = { @InnerField(suffix = "raw", type = FieldType.Keyword) })
	private String title;

	@Field(type = FieldType.Nested, includeInParent = true)
	private List<Artist> artists = new ArrayList<>();

	@Field(type = FieldType.Text)
	private String album;

	@Field(type = FieldType.Text)
	private String description;

	@Field(type = FieldType.Text)
	private TrackFormat format;

	@Field(type = FieldType.Text)
	private List<String> tags = new ArrayList<>();

}
