# Audio format fixtures

Real 1 second, 8 kHz mono 440 Hz sine tones, small enough to keep in the repository. The upload API identifies an
audio format by the file name's extension, and the waveform computation decodes them with ffmpeg, so they must be
playable. They were generated with:

```sh
ffmpeg -f lavfi -i "sine=frequency=440:duration=1:sample_rate=8000" -ac 1 tune.wav    # and tune.m4a, tune.flac
ffmpeg -f lavfi -i "sine=frequency=440:duration=1:sample_rate=8000" -ac 1 -c:a libvorbis tune.ogg
```
