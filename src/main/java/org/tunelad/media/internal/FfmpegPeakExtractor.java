package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Computes the peaks of a waveform by decoding audio with an ffmpeg process to 8 kHz mono 16-bit PCM.
 * <p>
 * Process I/O blocks, so it all runs on the bounded elastic scheduler, never on an event loop thread: the audio is
 * written to ffmpeg's stdin by one task while another reads the PCM from its stdout (one task doing both could
 * deadlock once the pipes fill up). Nothing is buffered whole: the audio streams through, and only window maxima of
 * the decoded samples are kept (see {@link Peaks}).
 */
@Slf4j
@Component
public class FfmpegPeakExtractor {

    private final FfmpegProperties properties;

    FfmpegPeakExtractor(FfmpegProperties properties) {
        this.properties = properties;
    }

    /**
     * The peaks of the audio, read once subscribed. Fails with {@link FfmpegUnavailableException} when ffmpeg cannot
     * be started, and with {@link UndecodableAudioException} when it cannot decode the audio or takes longer than the
     * configured timeout (the process is then stopped).
     */
    public Mono<double[]> peaks(Flux<DataBuffer> audio) {
        return Mono.using(this::start,
                        process -> Mono.fromCallable(() -> decode(process, audio))
                                .subscribeOn(Schedulers.boundedElastic()),
                        Process::destroyForcibly)
                .timeout(properties.timeout(), Mono.error(() -> new UndecodableAudioException(
                        "ffmpeg did not decode the audio within " + properties.timeout())));
    }

    private Process start() {
        var command = List.of(properties.path(), "-hide_banner", "-loglevel", "error", "-nostdin",
                "-i", "pipe:0", "-f", "s16le", "-ac", "1", "-ar", "8000", "pipe:1");
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new FfmpegUnavailableException(properties.path(), e);
        }
    }

    private double[] decode(Process process, Flux<DataBuffer> audio) throws IOException, InterruptedException {
        // ffmpeg's error messages, for the exception; read alongside so a chatty ffmpeg cannot block on stderr
        var errors = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()),
                command -> Schedulers.boundedElastic().schedule(command));
        writeInBackground(audio, process.getOutputStream());

        var accumulator = new Peaks.Accumulator();
        try (InputStream pcm = process.getInputStream()) {
            var buffer = new byte[64 * 1024];
            for (int read; (read = pcm.read(buffer)) != -1; ) {
                accumulator.accept(buffer, 0, read);
            }
        }
        int exitCode = process.waitFor(10, TimeUnit.SECONDS) ? process.exitValue() : -1;
        if (exitCode != 0 || accumulator.samples() == 0) {
            throw new UndecodableAudioException("ffmpeg could not decode the audio (exit code %d): %s"
                    .formatted(exitCode, errors.join().strip()));
        }
        return accumulator.peaks();
    }

    /** Streams the audio to ffmpeg's stdin, closing it at the end so ffmpeg knows the input is complete. */
    static void writeInBackground(Flux<DataBuffer> audio, OutputStream stdin) {
        DataBufferUtils.write(audio.publishOn(Schedulers.boundedElastic()), stdin)
                .doOnNext(DataBufferUtils::release)
                .doFinally(signal -> closeQuietly(stdin))
                // ffmpeg stops reading early when it gives up on the input: the broken pipe is expected then
                .subscribe(null, error -> log.debug("Stopped writing audio to ffmpeg: {}", error.toString()));
    }

    static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static void closeQuietly(OutputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // the process is gone: nothing left to tell it
        }
    }
}
