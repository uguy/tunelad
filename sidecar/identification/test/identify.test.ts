import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { AddressInfo } from 'node:net';
import { start } from '../src/server.ts';

// The recording of two reels used as a fixture by the application's own tests.
const FIXTURE = path.resolve(path.dirname(fileURLToPath(import.meta.url)),
  '../../../src/test/resources/mp3/Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3');
const MOSSY_BANKS = '1846';

let service: Awaited<ReturnType<typeof start>>;
let url: string;

before(async () => {
  service = await start(0, process.env['INDEX_DIR'] ?? '.cache', () => {});
  url = `http://localhost:${(service.server.address() as AddressInfo).port}`;
}, { timeout: 120_000 });

after(() => service.close());

const identify = (tunes: string, body: Uint8Array = fs.readFileSync(FIXTURE)) =>
  fetch(`${url}/identify?tunes=${tunes}`, { method: 'POST', body });

test('health is up once the engine is loaded', async () => {
  const response = await fetch(`${url}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: 'UP' });
});

test('hears The Mossy Banks from the start of the recording', async () => {
  const response = await identify(`${MOSSY_BANKS},9999999`);
  assert.equal(response.status, 200);
  const detections = await response.json() as { tuneId: string; name: string; start: number; end: number; confidence: number }[];
  const mossy = detections.find(d => d.tuneId === MOSSY_BANKS);
  assert.ok(mossy, `The Mossy Banks not in ${JSON.stringify(detections)}`);
  assert.ok(mossy.start >= 0 && mossy.start <= 15, `starts at ${mossy.start}`);
  assert.ok(mossy.end > 90, `ends at ${mossy.end}`);
  assert.ok(mossy.confidence > 0.4, `confidence ${mossy.confidence}`);
}, { timeout: 120_000 });

test('only reports the tunes asked for', async () => {
  const response = await identify('9999999');
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), []);
}, { timeout: 120_000 });

test('refuses a request without tunes', async () => {
  assert.equal((await fetch(`${url}/identify`, { method: 'POST', body: 'x' })).status, 400);
});

test('refuses an empty body', async () => {
  assert.equal((await identify(MOSSY_BANKS, new Uint8Array())).status, 400);
});

test('refuses audio it cannot decode', async () => {
  assert.equal((await identify(MOSSY_BANKS, Buffer.from('not audio'))).status, 422);
});

test('unknown paths are 404', async () => {
  assert.equal((await fetch(`${url}/nope`)).status, 404);
});
