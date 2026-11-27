package org.tunelad.media.event;

/**
 * The audio stored under the key can be played, or can not: the rendition of audio a browser cannot play was made, or
 * its conversion failed. It never says {@code PENDING}, which a rendition stays in while it cannot be made, and it is
 * not raised for audio that needs no rendition. The key is the one the audio is stored under.
 */
public record AudioPrepared(String key) {}
