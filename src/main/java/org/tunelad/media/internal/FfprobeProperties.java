package org.tunelad.media.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How the media module runs ffprobe to read the tags of audio.
 *
 * @param path    the ffprobe executable: a name looked up on the {@code PATH}, or an absolute path
 * @param timeout how long one reading may take before ffprobe is stopped
 */
@ConfigurationProperties("tunelad.media.ffprobe")
public record FfprobeProperties(@DefaultValue("ffprobe") String path, @DefaultValue("30s") Duration timeout) {
}
