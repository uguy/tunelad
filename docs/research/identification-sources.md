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
