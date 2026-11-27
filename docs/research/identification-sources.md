# Identification sources: FolkFriend and irishtune.info

Researched 2026-11-27 for slice #823. Web search only; nothing below was tried against the live services. Items marked **unverified** need a check before building on them.

## FolkFriend

- Open source (GPL v3), by Tom Wyllie: <https://github.com/TomWyllie/folkfriend>. The app is a PWA at folkfriend.app; the core is a **Rust library compiled to WebAssembly**, plus unmaintained Python scripts.
- **No documented public HTTP API** was found. Using it means embedding the library or running its CLI, not calling a service.
- Its tune ids come from thesession.org, the same ids Tune Lad uses, so matches map directly to Tunes.
- It is built for short live or recorded melodies, not full band recordings: **unverified**.
- Licence consequence: linking GPL v3 code into the application would make the combined work GPL; running it as a separate process (CLI or sidecar container) avoids that. **Unverified**, needs a licence read.

## irishtune.info

- Non-profit, expert-verified index (8,000+ tunes, 30,000+ recordings in the discography).
- **No official public API and no published data licence** were found. The only programmatic access is a third-party scraper wrapping the pages (<https://github.com/Batpapa/irishtuneinfo-scraper-api>), whose author reports the site's content signals as `ai-train=no, search=yes, ai-input=yes`. That is the scraper author's reading, **unverified**.
- It can help match a Tune and find references or aliases (ADR 0001), but scraping it needs the owner's permission.

## Outcome

- Neither source offers a ready HTTP service. The design assumed `WebClient` adapters; that no longer holds.
- FolkFriend is the only candidate that analyses audio, and it would run as a local process or sidecar.
- irishtune.info should not be scraped without asking its owner.
- Band-recording quality of FolkFriend is still unknown; only a real test on our own recordings answers it.

## Options

1. **FolkFriend as a sidecar** (own container running its CLI, GPL kept separate); drop irishtune.info for now. The port in `design.md` stays.
2. Same, plus ask the irishtune.info owner for permission or a data export.
3. Pause the slice until a quick spike shows FolkFriend works on our recordings.

## Spike (2026-11-27): FolkFriend fork as a native CLI

Built `Batpapa/folkfriend` (HEAD `0d1531b`, GPL-3.0) in Docker (`rust:1-slim` plus `pkg-config` and `libssl-dev`, `cargo build --release`): **it builds as a native binary**, no WASM needed. `folkfriend query <wav>` loads the ~33 MB index (downloaded to `~/.folkfriend/` on first run from `folkfriend-app-data.web.app`, so the sidecar must cache it on a volume) and prints ranked `tuneId, name, score` lines.

- Input must be **16-bit PCM WAV**, any rate from 3952 to 66974 Hz (48 kHz default); ffmpeg converts our mp3/m4a/flac/ogg in the same image.
- Speed: **1.3 s for a 207 s file**, so analysis is cheap; the cost is decoding.
- Known sample (`soup_dragon.wav`, 9 s): top hit "soup dragon, the" with score 0.53, next 0.38, a correct match with a clear margin.
- Non-Irish pop track (`Knees_Up.mp3`): scores 0.10 to 0.35 with different junk per 10 s window, i.e. low confidence is visible in the score.
- The CLI queries the **whole input as one tune**: windowing (10 s window, 5 s hop) and segmentation live in Cadence's TypeScript, not in the Rust CLI. Our service must cut windows (ffmpeg) and implement the segmenter in Java.
- **Not tested**: a real Irish band recording or a set of several tunes; needs an actual Track from the owner.
- Output is plain text, not JSON, and the CLI has no HTTP mode: the sidecar needs a thin wrapper (shell script or tiny Rust/HTTP shim) or the Java side runs it per window through the container.

Decision: option C. Sidecar CLI now behind the `IdentificationSource` port, irishtune.info dropped from this slice, a pure-Java segmenter constrained to the Track's Set, and a Java port of FolkFriend left for a later slice (GPL-3 or clean-room).

### Spike result on a real recording (negative)

File: `Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3` (set of two reels, 152 s, a duo). Converted to 48 kHz mono WAV and cut into 29 windows of 10 s with a 5 s hop, as Cadence does.

- Whole file as one query: unrelated tunes, top score 0.20.
- Per window, default mode: top scores 0.04 to 0.33, a different winner nearly every window, never a stable tune over several windows.
- With pitch-class mode and any-pitch enabled (the library has them, off by default; I patched the CLI locally to switch them on with env vars): scores 0.09 to 0.29, still no stable winner.
- "The Mossy Banks" is in the index (tune 1846) but appears in **no window's top 10** in either mode. A name search for "raven" finds nothing, so "Raven's Rock" may not be in the index at all, in which case it can never be identified.
- The fork's CLI is not Cadence's pipeline: Cadence feeds FolkFriend through its own transcriptor (added 2026-11-26) and its Viterbi segmenter, neither of which I ran. So this shows the bare CLI fails on this file, not that Cadence would.
- Only one recording was tested.

Consequence: FolkFriend through the bare CLI is **not** good enough to base Identification on, at least for duos. Before building the sidecar, decide between testing Cadence's transcriptor path, testing more recordings, or changing the approach (see the proposal's open questions).

### Spike result with Cadence's pipeline (positive)

Cadence (`Batpapa/Cadence`, AGPL-3.0) run headless in Node with a throwaway vitest harness that copies `ffWorker.ts`'s init and window loop: the vendored FolkFriend WASM (SIMD), the transcriptor WASM, the same index, `TranscriptorEngine` and `IncrementalViterbiSegmenter`, 10 s windows and 5 s hop, on the same duo recording. 8 s of run time for 152 s of audio.

- **"The Mossy Banks" is the top candidate in every window from 5 s to 105 s**, with scores 0.47 to 0.76 and the runner-up usually 0.1 to 0.2 lower. The segmenter returns one detection for it, 7.5 s to 112.5 s.
- No stable candidate after 110 s: the second reel never appears. "Raven's Rock" is probably absent from the thesession.org index (a name search for "raven" finds nothing), so no engine built on this index can name it.
- The segmenter's last detection, "the boys of the lough" (142.5 s to 150 s, confidence 0.45), is probably a false positive at the end of the file.
- The start offset 7.5 s is the middle of the first window, not measured against the audio; precision is about half a hop.
- Only one recording was tested.

What made the difference against the bare CLI: the transcriptor's note decoder (pitch classes summed over instruments, drone removal), the query tuned for pitch classes in every key, and the segmenter that merges 21 windows into one detection.

Consequence: recognition is feasible, but the engine is **Cadence's TypeScript and WASM code (AGPL-3.0 and GPL-3.0)**, not the Rust CLI. See the proposal's design for how to host it.
