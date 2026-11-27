# identification sidecar

Hears which tunes are played in a recording. It runs [Cadence](https://github.com/Batpapa/Cadence)'s recognition engine
(FolkFriend WebAssembly, note decoder, Viterbi segmenter) in Node, behind a small HTTP API that the application's
`identification` module calls. See `NOTICE` for the licences, and `docs/research/identification-sources.md` for why.

## API

- `POST /identify?tunes=<id>,<id>`: the body is the audio in any format ffmpeg reads. `tunes` are thesession.org tune
  ids (the Tunes of the Track's Set); only those can be reported. Answers `200` with a list in time order:

  ```json
  [{ "tuneId": "1846", "name": "the mossy banks", "start": 7.5, "end": 112.5, "confidence": 0.60 }]
  ```

  Times are seconds. Analysis uses 10 s windows every 5 s, so boundaries are accurate to about 2.5 s. `400` without
  `tunes` or body, `413` over `MAX_UPLOAD_BYTES`, `422` when ffmpeg cannot decode the audio.
- `GET /health`: `200` once the engine and the index are loaded (the first start downloads the index), else `503`.

## Run

```sh
docker compose up -d identification   # from the repository root, on port 8090
cd sidecar/identification && npm ci && npm test     # needs Node 22+, ffmpeg and network for the index (cached in .cache)
```

Settings (environment): `PORT` (8090), `INDEX_DIR` (`.cache`, `/data` in the image), `MAX_AUDIO_SECONDS` (3600),
`MAX_UPLOAD_BYTES` (200 MB).

The analysis takes about 5 % of the audio's duration and runs in one worker thread, one request at a time.
