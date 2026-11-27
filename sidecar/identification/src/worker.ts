import { parentPort, workerData } from 'node:worker_threads';
import { Identifier } from './identifier.ts';

/** Owns the engine in its own thread, so that the CPU-bound analysis never stalls the HTTP server. */
const identifier = await Identifier.create(workerData.cacheDir, message => parentPort!.postMessage({ type: 'log', message }));
parentPort!.postMessage({ type: 'ready' });

parentPort!.on('message', (job: { id: number; pcm: Float32Array; tunes?: string[] }) => {
  try {
    const detections = identifier.identify(job.pcm, job.tunes ? new Set(job.tunes) : undefined);
    parentPort!.postMessage({ type: 'result', id: job.id, detections });
  } catch (error) {
    parentPort!.postMessage({ type: 'failure', id: job.id, message: error instanceof Error ? error.message : String(error) });
  }
});
