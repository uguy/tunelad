package org.tunelad.media.internal;

/** ffmpeg ran but could not decode the audio: it is not in a format ffmpeg knows, or it is damaged. */
public class UndecodableAudioException extends RuntimeException {

    UndecodableAudioException(String message) {
        super(message);
    }
}
