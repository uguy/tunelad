// Retakes the UI screenshots of the README from the built frontend, with fixture data in place of the API.
//
//   cd app && bun run build && cd .. && node doc/screenshots.mjs
//
// It serves nothing itself: it starts `vite preview` (port 4173) when nothing listens there, mocks the REST API with
// page.route (alice logged in, a few tracks, favourites, playlists, waveforms) and writes doc/*.png. The data is fixed
// and the animations are off, so running it twice gives the same images. The Scalar and OpenAPI pages come from the
// running backend, not from this script: see "Scalar UI and OpenAPI" in the README.
import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '../app/node_modules/@playwright/test/index.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const BASE = 'http://localhost:4173';

const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const BOB = { id: 'account-bob', displayName: 'Bob Smith', admin: false };
const track = (n, title, description, owner, tags = []) => ({
	id: `track-${n}`,
	title,
	description,
	artist: null,
	album: null,
	format: 'MP3',
	tags,
	owner
});
const TRACKS = [
	track(1, 'Knees Up', 'A reel and a song, recorded at the Saturday session', ALICE, ['reel']),
	track(2, 'Mossy Banks', 'Ravens Rock, from the Doocey and Doherty duo', BOB, ['jig']),
	track(3, 'Frenzy of the Meeting', 'The tune the whole room joins in on', BOB, ['reel']),
	track(4, 'The Butterfly', 'A slip jig for the last round', ALICE, ['slip jig']),
	track(5, 'Slow Air for Sunday', 'Played late, once the glasses are empty', BOB, ['air'])
];
const FAVOURITES = [TRACKS[1], TRACKS[0], TRACKS[3]];
const summary = (id, name, description, tracks) => ({
	id,
	name,
	description,
	trackCount: tracks.length,
	updatedAt: '2026-11-27T10:00:00Z'
});
const SESSION = { id: 'p1', name: 'Saturday session', description: 'Set order for the pub', tracks: [TRACKS[2], TRACKS[0], TRACKS[1], TRACKS[3]] };
const SLOW = { id: 'p2', name: 'Late night', description: null, tracks: [TRACKS[4]] };
const PLAYLISTS = [SESSION, SLOW];
const PEAKS = Array.from({ length: 200 }, (_, i) =>
	Math.min(1, 0.25 + 0.55 * Math.abs(Math.sin(i / 7)) * (0.6 + 0.4 * Math.abs(Math.sin(i / 23))))
);

async function mockApi(page) {
	const json = (route, body) => route.fulfill({ json: body });
	await page.route('**/api/me', (r) => json(r, ALICE));
	await page.route('**/api/me/favourites', (r) => json(r, FAVOURITES));
	await page.route('**/api/tracks/search*', (r) => json(r, TRACKS));
	await page.route('**/api/tracks/*/waveform', (r) => json(r, { status: 'READY', peaks: PEAKS }));
	// The audio is not loaded: the player's clock then stays at zero, so that every run gives the same image
	await page.route('**/api/tracks/*/play', (r) => r.abort());
	await page.route('**/api/playlists', (r) =>
		json(r, PLAYLISTS.map((p) => summary(p.id, p.name, p.description, p.tracks)))
	);
	await page.route('**/api/playlists/*', (r) => {
		const found = PLAYLISTS.find((p) => r.request().url().endsWith(`/${p.id}`));
		return found
			? json(r, { ...summary(found.id, found.name, found.description, found.tracks), tracks: found.tracks })
			: r.fulfill({ status: 404 });
	});
	// No live stream: the pages are shown as they are after their first load
	await page.route('**/api/live', (r) => r.abort());
}

async function listening() {
	try {
		await fetch(BASE);
		return true;
	} catch {
		return false;
	}
}

const server = (await listening())
	? null
	: spawn('bun', ['run', 'preview'], { cwd: join(here, '../app'), stdio: 'ignore' });
for (let i = 0; i < 60 && !(await listening()); i++) await new Promise((r) => setTimeout(r, 500));

const browser = await chromium.launch();
try {
	const shot = async (name, path, { scheme = 'light', ready, play } = {}) => {
		const context = await browser.newContext({
			viewport: { width: 1280, height: 800 },
			colorScheme: scheme,
			reducedMotion: 'reduce',
			serviceWorkers: 'block'
		});
		const page = await context.newPage();
		await mockApi(page);
		// The player shows "playing", but no sound is played: the clock stays at 0:00, so every run gives the same image
		await page.addInitScript(() => {
			HTMLMediaElement.prototype.play = () => Promise.resolve();
		});
		await page.goto(BASE + path);
		await page.getByTestId('track-title').first().waitFor().catch(() => {});
		await ready?.(page);
		if (play) await page.getByRole('button', { name: play }).click();
		// Everything is drawn: the fonts, the requests, and the waveform of each track
		await page.waitForLoadState('networkidle');
		await page.evaluate(() => document.fonts.ready);
		await page.waitForFunction(
			() => document.querySelectorAll('[data-testid="waveform-placeholder"]').length === 0
		);
		await page.waitForTimeout(1000);
		await page.screenshot({ path: join(here, `${name}.png`) });
		await context.close();
		console.log(`doc/${name}.png`);
	};

	const play = 'play track Knees Up button';
	await shot('track-list', '/tracks', { play });
	await shot('track-list-dark', '/tracks', { scheme: 'dark', play });
	await shot('favourites', '/tracks/favourites');
	await shot('playlist', '/tracks/playlists/p1', { play: 'play track Mossy Banks button' });
	await shot('account', '/account');
} finally {
	await browser.close();
	server?.kill();
}
