import * as http from 'node:http';
import { spawn } from 'node:child_process';
import { Worker } from 'node:worker_threads';
import { fileURLToPath } from 'node:url';
import type { Detection } from './identifier.ts';
import { SAMPLE_RATE } from './identifier.ts';

/** The longest audio analysed, in seconds; the rest is ignored. */
const MAX_SECONDS = Number(process.env['MAX_AUDIO_SECONDS'] ?? 3600);
/** The largest upload accepted, in bytes. */
const MAX_BYTES = Number(process.env['MAX_UPLOAD_BYTES'] ?? 200 * 1024 * 1024);

/** Decodes any audio ffmpeg reads to mono 32-bit float PCM at {@link SAMPLE_RATE}. */
export function decode(audio: Buffer): Promise<Float32Array> {
  return new Promise((resolve, reject) => {
    const ffmpeg = spawn('ffmpeg', ['-v', 'error', '-i', 'pipe:0', '-t', String(MAX_SECONDS), '-vn', '-ac', '1', '-ar', String(SAMPLE_RATE), '-f', 'f32le', 'pipe:1']);
    const chunks: Buffer[] = [];
    let stderr = '';
    ffmpeg.stdout.on('data', chunk => chunks.push(chunk));
    ffmpeg.stderr.on('data', chunk => { stderr += chunk; });
    ffmpeg.on('error', reject);
    ffmpeg.stdin.on('error', () => { /* ffmpeg may stop reading early: its exit code tells */ });
    ffmpeg.on('close', code => {
      if (code !== 0) return reject(new Error(`ffmpeg could not decode the audio: ${stderr.trim().split('\n').pop()}`));
      const bytes = Buffer.concat(chunks);
      resolve(new Float32Array(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.length - (bytes.length % 4))));
    });
    ffmpeg.stdin.end(audio);
  });
}

function readBody(request: http.IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    request.on('data', (chunk: Buffer) => {
      size += chunk.length;
      if (size > MAX_BYTES) { reject(new HttpError(413, 'The audio is too large.')); request.destroy(); return; }
      chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', reject);
  });
}

class HttpError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}

/** An HTTP server whose analyses run one at a time in a worker thread. */
export async function start(port: number, cacheDir: string, log: (message: string) => void = console.log): Promise<{ server: http.Server; close(): Promise<void> }> {
  const worker = new Worker(fileURLToPath(new URL('./worker-boot.mjs', import.meta.url)), { workerData: { cacheDir } });
  const pending = new Map<number, { resolve(d: Detection[]): void; reject(e: Error): void }>();
  let ready = false;
  let nextId = 1;
  let queue: Promise<unknown> = Promise.resolve();
  await new Promise<void>((resolve, reject) => {
    worker.on('error', reject);
    worker.on('message', message => {
      if (message.type === 'log') log(`[identification] ${message.message}`);
      else if (message.type === 'ready') { ready = true; resolve(); }
      else if (message.type === 'result') { pending.get(message.id)?.resolve(message.detections); pending.delete(message.id); }
      else if (message.type === 'failure') { pending.get(message.id)?.reject(new Error(message.message)); pending.delete(message.id); }
    });
  });
  worker.on('error', error => { ready = false; log(`[identification] worker failed: ${error}`); });

  const analyse = (pcm: Float32Array, tunes?: string[]) => {
    const run = () => new Promise<Detection[]>((resolve, reject) => {
      const id = nextId++;
      pending.set(id, { resolve, reject });
      worker.postMessage({ id, pcm, tunes }, [pcm.buffer]);
    });
    const result = queue.then(run, run);
    queue = result.catch(() => undefined);
    return result;
  };

  const server = http.createServer(async (request, response) => {
    const send = (status: number, body: unknown) => {
      response.writeHead(status, { 'content-type': 'application/json' }).end(JSON.stringify(body));
    };
    try {
      const url = new URL(request.url ?? '/', 'http://localhost');
      if (request.method === 'GET' && url.pathname === '/health') {
        return send(ready ? 200 : 503, { status: ready ? 'UP' : 'DOWN' });
      }
      if (request.method === 'POST' && url.pathname === '/identify') {
        // The tunes of the Track's Set, as thesession.org ids: only these can be reported
        const tunes = url.searchParams.get('tunes')?.split(',').map(t => t.trim()).filter(Boolean);
        if (!tunes?.length) throw new HttpError(400, 'The tunes query parameter lists the thesession.org ids to look for.');
        const body = await readBody(request);
        if (body.length === 0) throw new HttpError(400, 'The request body is the audio.');
        const pcm = await decode(body).catch(error => { throw new HttpError(422, error.message); });
        const seconds = Math.round(pcm.length / SAMPLE_RATE);  // the worker takes the buffer over
        const started = Date.now();
        const detections = await analyse(pcm, tunes);
        log(`[identification] ${seconds} s of audio, ${detections.length} detections, ${Date.now() - started} ms`);
        return send(200, detections);
      }
      throw new HttpError(404, 'Not found.');
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 500;
      if (status === 500) log(`[identification] ${error instanceof Error ? error.stack : error}`);
      send(status, { error: error instanceof Error ? error.message : String(error) });
    }
  });
  await new Promise<void>(resolve => server.listen(port, resolve));
  return {
    server,
    close: async () => { await new Promise(resolve => server.close(resolve)); await worker.terminate(); },
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const port = Number(process.env['PORT'] ?? 8090);
  await start(port, process.env['INDEX_DIR'] ?? '.cache');
  console.log(`[identification] listening on ${port}`);
}
