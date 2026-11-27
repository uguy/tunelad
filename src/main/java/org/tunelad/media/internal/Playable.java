package org.tunelad.media.internal;

import java.util.Set;

/** Which audio a browser plays as it is stored, and the key its rendition is stored under when it does not. */
final class Playable {

    /** The content type of a rendition: AAC in an mp4 container, which every browser plays. */
    static final String RENDITION_CONTENT_TYPE = "audio/mp4";

    private static final String RENDITION_SUFFIX = ":play";

    private static final Set<String> BY_BROWSERS = Set.of("audio/mpeg", "audio/mp4", "audio/ogg", "audio/flac", "audio/wav");

    private Playable() {
    }

    /** True when browsers play audio of this content type, so that it needs no rendition. */
    static boolean byBrowsers(String contentType) {
        return contentType == null || contentType.isBlank() || BY_BROWSERS.contains(contentType);
    }

    /** The key the rendition of the audio stored under this key is stored under. */
    static String renditionKey(String key) {
        return key + RENDITION_SUFFIX;
    }

    static boolean isRenditionKey(String key) {
        return key.endsWith(RENDITION_SUFFIX);
    }
}
