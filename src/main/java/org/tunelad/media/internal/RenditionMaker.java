package org.tunelad.media.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Converts audio a browser cannot play to AAC in an mp4 container, with an ffmpeg process.
 * <p>
 * The mp4 muxer seeks back to write its index once it is done, which a pipe does not allow, so ffmpeg writes a
 * temporary file; it goes once the caller is through with it. Process and file work blocks, so it all runs on the
 * bounded elastic scheduler, never on an event loop thread, and the audio streams to ffmpeg's stdin as it does for
 * waveforms (see {@link FfmpegPeakExtractor}).
 */
@Slf4j
@Component
public class RenditionMaker {

    private final FfmpegProperties properties;

    RenditionMaker(FfmpegProperties properties) {
        this.properties = properties;
    }

    /**
     * Converts the audio, then hands the rendition to {@code use} and deletes it when that completes, fails or is
     * cancelled. The conversion is done before {@code use} starts, so that a slow encoding holds nothing open (a
     * database transaction, say). Fails with {@link FfmpegUnavailableException} when ffmpeg cannot be started, and
     * with {@link UndecodableAudioException} when it cannot convert the audio or takes longer than the configured
     * conversion timeout (the process is then stopped).
     */
    public <T> Mono<T> using(Flux<DataBuffer> audio, Function<Flux<DataBuffer>, Mono<T>> use) {
        return Mono.usingWhen(convert(audio),
                file -> use.apply(DataBufferUtils.read(file, DefaultDataBufferFactory.sharedInstance, 64 * 1024)),
                file -> Mono.fromRunnable(() -> delete(file)).subscribeOn(Schedulers.boundedElastic()),
                (file, error) -> Mono.fromRunnable(() -> delete(file)).subscribeOn(Schedulers.boundedElastic()),
                file -> Mono.fromRunnable(() -> delete(file)).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<Path> convert(Flux<DataBuffer> audio) {
        return Mono.fromCallable(() -> run(audio))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(properties.transcodeTimeout().plusSeconds(15), Mono.error(() -> new UndecodableAudioException(
                        "ffmpeg did not convert the audio within " + properties.transcodeTimeout())));
    }

    private Path run(Flux<DataBuffer> audio) throws IOException, InterruptedException {
        var file = Files.createTempFile("tunelad-rendition-", ".m4a");
        var process = start(file);
        try {
            var errors = CompletableFuture.supplyAsync(() -> FfmpegPeakExtractor.readAll(process.getErrorStream()),
                    command -> Schedulers.boundedElastic().schedule(command));
            FfmpegPeakExtractor.writeInBackground(audio, process.getOutputStream());
            // Nothing is expected on stdout: the output goes to the file
            process.getInputStream().close();
            if (!process.waitFor(properties.transcodeTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new UndecodableAudioException(
                        "ffmpeg did not convert the audio within " + properties.transcodeTimeout());
            }
            if (process.exitValue() != 0 || Files.size(file) == 0) {
                throw new UndecodableAudioException("ffmpeg could not convert the audio (exit code %d): %s"
                        .formatted(process.exitValue(), errors.join().strip()));
            }
            return file;
        } catch (IOException | InterruptedException | RuntimeException e) {
            delete(file);
            throw e;
        } finally {
            process.destroyForcibly();
        }
    }

    private Process start(Path output) throws IOException {
        var command = List.of(properties.path(), "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-i", "pipe:0", "-vn", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
                "-f", "mp4", output.toString());
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            Files.deleteIfExists(output);
            throw new FfmpegUnavailableException(properties.path(), e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete the temporary rendition {}", file, e);
        }
    }
}
