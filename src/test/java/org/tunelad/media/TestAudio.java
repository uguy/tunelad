package org.tunelad.media;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Audio made up for tests. */
public final class TestAudio {

    private TestAudio() {
    }

    /** A 4 second 8 kHz mono wav file: 2 seconds of silence, then 2 seconds of a loud 440 Hz tone. */
    public static byte[] silenceThenTone() {
        int rate = 8000, samples = rate * 4;
        var pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            pcm.putShort(i < samples / 2 ? 0 : (short) (Math.sin(2 * Math.PI * 440 * i / rate) * 30000));
        }
        return wav(pcm.array(), rate);
    }

    /** Bytes no decoder makes sense of. */
    public static byte[] noise() {
        var bytes = new byte[64 * 1024];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    private static byte[] wav(byte[] pcm, int rate) {
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(pcm.length);
        var out = new ByteArrayOutputStream();
        out.writeBytes(header.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }
}
