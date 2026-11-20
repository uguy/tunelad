package org.tunelad.media.internal;

/** ffmpeg could not be started, most likely because it is not installed: no audio can be decoded until it is. */
public class FfmpegUnavailableException extends RuntimeException {

    FfmpegUnavailableException(String path, Throwable cause) {
        super("Could not run ffmpeg (" + path + "): install it, or set tunelad.media.ffmpeg.path", cause);
    }
}
