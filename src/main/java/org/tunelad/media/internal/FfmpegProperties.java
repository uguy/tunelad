package org.tunelad.media.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How the media module runs ffmpeg to decode audio for waveforms.
 *
 * @param path    the ffmpeg executable: a name looked up on the {@code PATH}, or an absolute path
 * @param timeout how long one decoding may take before ffmpeg is stopped
 */
@ConfigurationProperties("tunelad.media.ffmpeg")
public record FfmpegProperties(@DefaultValue("ffmpeg") String path, @DefaultValue("60s") Duration timeout) {
}
