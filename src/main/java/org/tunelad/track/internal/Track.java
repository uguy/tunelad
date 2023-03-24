package org.tunelad.track.internal;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.tunelad.track.TrackFormat;

@Data
@Entity
public class Track  {

	@Id
	private String id;

	@Version
	private int version;

	@Audited
	private String title;

	@Audited
	private String artist;

	@Audited
	private String album;

	@Audited
	private String description;

	private String artUrl;

	@Lob
//	@Basic(fetch = FetchType.LAZY)
	private byte[] data;

	private TrackFormat format;
}
