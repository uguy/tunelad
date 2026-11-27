import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import initFolkFriend, { FolkFriendWASM } from '../engine/vendor/folkfriend/folkfriend.js';
import initTranscriptor from '../engine/vendor/cadence-transcriptor/cadence_transcriptor.js';
import { useAnalysis } from '../engine/transcriptor/src/decoder.ts';
import { nativeAnalyse } from '../engine/src/session/recognition/transcriptorInit.ts';
import { configureForQuery, TuneQuery } from '../engine/src/session/recognition/folkfriendQuery.ts';
import { PlayedPitch } from '../engine/src/session/recognition/playedPitch.ts';
import { TranscriptorEngine } from '../engine/src/session/recognition/transcriptorEngine.ts';
import { IncrementalViterbiSegmenter } from '../engine/src/session/recognition/viterbiSegmenter.ts';
import { ANALYSIS_HOP_S, ANALYSIS_WINDOW_S, TUNE_INDEX_URL } from '../engine/src/session/sessionConfig.ts';

/** The sample rate the audio is decoded to: Cadence measured 22 kHz as degrading transcription badly. */
export const SAMPLE_RATE = 48_000;

/** One tune the analysis heard, and when. Times are seconds from the start of the audio. */
export interface Detection {
  tuneId: string;
  name: string;
  start: number;
  end: number;
  confidence: number;
}

const engineDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../engine');

/** Downloads the thesession.org tune index the first time and keeps it in `cacheDir`. */
async function loadIndex(cacheDir: string, log: (message: string) => void): Promise<unknown> {
  const file = path.join(cacheDir, 'folkfriend-non-user-data.json');
  if (!fs.existsSync(file)) {
    log(`downloading the tune index from ${TUNE_INDEX_URL}`);
    const response = await fetch(TUNE_INDEX_URL);
    if (!response.ok) throw new Error(`The tune index download failed: HTTP ${response.status}`);
    fs.mkdirSync(cacheDir, { recursive: true });
    const partial = `${file}.${process.pid}.part`;
    fs.writeFileSync(partial, Buffer.from(await response.arrayBuffer()));
    fs.renameSync(partial, file);
  }
  return JSON.parse(fs.readFileSync(file, 'utf-8'));
}

/**
 * Cadence's recognition engine over a whole recording: 10 s windows every 5 s, each heard by the note decoder and
 * queried against the index, then segmented into detections by the Viterbi segmenter, exactly as the app does live.
 */
export class Identifier {
  private constructor(private readonly folkFriend: FolkFriendWASM, private readonly playedPitch: PlayedPitch) {}

  static async create(cacheDir: string, log: (message: string) => void = () => {}): Promise<Identifier> {
    const wasm = (name: string) => fs.readFileSync(path.join(engineDir, 'vendor', name));
    await initTranscriptor(wasm('cadence-transcriptor/cadence_transcriptor_bg_simd.wasm'));
    useAnalysis(nativeAnalyse);
    await initFolkFriend(wasm('folkfriend/folkfriend_bg_simd.wasm'));
    const index = await loadIndex(cacheDir, log);
    const folkFriend = new FolkFriendWASM();
    configureForQuery(folkFriend);
    folkFriend.set_source_order([]);
    folkFriend.load_index_from_json_obj(index);
    const playedPitch = new PlayedPitch(tuneId => {
      const raw = JSON.parse(folkFriend.settings_from_tune_id(tuneId)) as Array<[string, { contour: string }]> | { error: string };
      return Array.isArray(raw) ? raw.map(([id, setting]) => [id, setting.contour] as [string, string]) : [];
    });
    log('ready');
    return new Identifier(folkFriend, playedPitch);
  }

  /**
   * The tunes heard in `pcm` (mono, {@link SAMPLE_RATE}), in time order. When `allowed` is given, detections of other
   * tunes are dropped. Synchronous and CPU-bound: about 5 % of the audio's duration.
   */
  identify(pcm: Float32Array, allowed?: ReadonlySet<string>): Detection[] {
    const engine = new TranscriptorEngine(SAMPLE_RATE, new TuneQuery(this.folkFriend, this.playedPitch));
    const segmenter = new IncrementalViterbiSegmenter(ANALYSIS_HOP_S);
    const window = ANALYSIS_WINDOW_S * SAMPLE_RATE;
    const hop = ANALYSIS_HOP_S * SAMPLE_RATE;
    const shown = new Map<string, Detection>();
    const apply = (events: ReturnType<IncrementalViterbiSegmenter['step']>) => {
      for (const event of events) {
        if (event.type === 'retract') {
          shown.delete(event.id);
        } else {
          const d = event.detection;
          shown.set(d.id, { tuneId: d.tuneId, name: d.displayName, start: d.start, end: d.end ?? d.start, confidence: d.confidence });
        }
      }
    };
    for (let end = window; end <= pcm.length; end += hop) {
      const { result } = engine.analyze(pcm.subarray(end - window, end), (end - window) / SAMPLE_RATE, end / SAMPLE_RATE);
      const events = segmenter.step(result);
      engine.stepped(events);
      apply(events);
    }
    apply(segmenter.finalize());
    return [...shown.values()]
      .filter(d => !allowed || allowed.has(d.tuneId))
      .sort((a, b) => a.start - b.start);
  }
}
