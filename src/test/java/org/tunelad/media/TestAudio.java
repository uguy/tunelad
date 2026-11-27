package org.tunelad.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
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

    /**
     * A 4 second WMA file, made by ffmpeg from {@link #silenceThenTone()} (so ffmpeg must be installed, see README):
     * no third-party recording is kept in the repository.
     */
    public static byte[] wma() {
        var dir = tempDir();
        try {
            var in = dir.resolve("tone.wav");
            var out = dir.resolve("tone.wma");
            Files.write(in, silenceThenTone());
            var process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-i", in.toString(), "-c:a", "wmav2", out.toString()).redirectErrorStream(true).start();
            var log = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg could not make a wma file: " + log);
            }
            return Files.readAllBytes(out);
        } catch (IOException e) {
            throw new IllegalStateException("ffmpeg must be installed to make a wma file", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * A 2 second file of the given format (its extension, such as {@code mp3} or {@code wma}) made by ffmpeg from
     * {@link #silenceThenTone()}, carrying these tags (name to value, as ffmpeg's {@code -metadata}); no third-party
     * recording is kept in the repository.
     */
    public static byte[] tagged(String extension, java.util.Map<String, String> tags) {
        var dir = tempDir();
        try {
            var in = dir.resolve("tone.wav");
            var out = dir.resolve("tagged." + extension);
            Files.write(in, silenceThenTone());
            var command = new java.util.ArrayList<>(java.util.List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-i", in.toString(), "-t", "2"));
            tags.forEach((name, value) -> command.addAll(java.util.List.of("-metadata", name + "=" + value)));
            command.add(out.toString());
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var log = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg could not make a tagged " + extension + " file: " + log);
            }
            return Files.readAllBytes(out);
        } catch (IOException e) {
            throw new IllegalStateException("ffmpeg must be installed to make a tagged file", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            deleteQuietly(dir);
        }
    }

    /** Bytes with the ASF header of a WMA file, followed by noise: a file no decoder can play. */
    public static byte[] fakeWma() {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11});
        out.writeBytes(noise());
        return out.toByteArray();
    }

    private static java.nio.file.Path tempDir() {
        try {
            return Files.createTempDirectory("tunelad-wma");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(java.nio.file.Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // a leftover temporary file is not worth failing a test
        }
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
