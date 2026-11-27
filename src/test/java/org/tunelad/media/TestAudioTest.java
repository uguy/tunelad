package org.tunelad.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real ffmpeg, which must be installed (see README). */
class TestAudioTest {

    private static final byte[] ASF_HEADER = {0x30, 0x26, (byte) 0xB2, 0x75};

    @Test
    void wma_is_an_asf_file() {
        assertThat(TestAudio.wma()).startsWith(ASF_HEADER);
    }

    @Test
    void fake_wma_has_the_asf_header_too() {
        assertThat(TestAudio.fakeWma()).startsWith(ASF_HEADER);
    }
}
