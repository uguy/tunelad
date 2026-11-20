# Audio format fixtures

The upload API identifies an audio format by the file name's extension only, and tests compare stored bytes, so
these fixtures don't have to be decodable:

- `tune.wav` is a real 1 second, 8 kHz mono sine tone.
- `tune.m4a`, `tune.ogg` and `tune.flac` start with their format's signature, followed by filler bytes. They are
  **not** playable audio.
