import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// UI-only tests: the REST API is mocked with page.route, serving real audio fixtures and
// honouring single byte ranges like GET /api/tracks/{id}/play does. Nobody is logged in
// (GET /api/me answers 401), so these also cover the media spec's anonymous playback.
const fixture = (path) =>
	readFileSync(fileURLToPath(new URL(`../../src/test/resources/${path}`, import.meta.url)));
const MP3 = { body: fixture('mp3/Knees_Up.mp3'), type: 'audio/mpeg' }; // about 207 seconds
const WAV = { body: fixture('audio/tune.wav'), type: 'audio/wav' }; // 1 second

const track = (id, title, owner) => ({
	id,
	title,
	description: `${title}, a tune`,
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: `account-${owner}`, displayName: owner }
});
// The list order is the queue's: a short tune first, so it ends quickly and the next one plays
const SHORT = track('track-0', 'Short Tune', 'Carol');
const KNEES_UP = track('track-1', 'Knees Up', 'Alice Liddell');
const REEL = track('track-2', 'Reel', 'Bob');
const AUDIO = { 'track-0': WAV, 'track-1': MP3, 'track-2': MP3 };

test.beforeEach(async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/tracks/search*', (route) =>
		route.fulfill({ json: [SHORT, KNEES_UP, REEL] })
	);
	await page.route('**/api/tracks/*/play', (route) => {
		const id = new URL(route.request().url()).pathname.split('/')[3];
		const { body, type } = AUDIO[id];
		const range = /^bytes=(\d*)-(\d*)$/.exec(route.request().headers()['range'] ?? '');
		const headers = { 'Content-Type': type, 'Accept-Ranges': 'bytes' };
		if (!range) return route.fulfill({ status: 200, headers, body });
		const first = range[1] ? Number(range[1]) : body.length - Number(range[2]);
		const last =
			range[1] && range[2] ? Math.min(Number(range[2]), body.length - 1) : body.length - 1;
		return route.fulfill({
			status: 206,
			headers: { ...headers, 'Content-Range': `bytes ${first}-${last}/${body.length}` },
			body: body.subarray(first, last + 1)
		});
	});
});

const audio = (page) => page.locator('#player');
const audioTime = (page) => audio(page).evaluate((a) => a.currentTime);
const playerBar = (page) => page.getByRole('region', { name: 'player' });
const playTrack = (page, title) =>
	page.getByRole('button', { name: `play track ${title} button` }).click();
/** Waits until the loaded audio's duration is known, so it can be seeked. */
const metadataLoaded = (page) =>
	expect.poll(() => audio(page).evaluate((a) => a.readyState)).toBeGreaterThanOrEqual(1);
const loadedTrackSrc = (page) => audio(page).getAttribute('src');

// --- Requirement: Start playback at a position from a link ---------------------------------

// Also the media spec's "Anonymous link with a start time": nobody is logged in
test('open a link with a start time', async ({ page }) => {
	await page.goto('/tracks?play=track-1&t=90');

	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-1/play#t=90');
	await expect.poll(() => audioTime(page)).toBeGreaterThanOrEqual(90);
});

test('open a link without a start time', async ({ page }) => {
	await page.goto('/tracks?play=track-1');

	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-1/play');
	await expect.poll(() => audioTime(page)).toBeLessThan(30);
});

test('open a link with an invalid start time', async ({ page }) => {
	await page.goto('/tracks?play=track-1&t=abc');

	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-1/play');
});

// --- Requirement: Copy a link to the current playback position -----------------------------

test('copy a link while a track is playing', async ({ page, context }) => {
	await context.grantPermissions(['clipboard-read', 'clipboard-write']);
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');

	await metadataLoaded(page);
	await audio(page).evaluate((a) => {
		a.pause();
		a.currentTime = 95.7;
	});
	await expect.poll(() => audioTime(page)).toBeCloseTo(95.7, 1);

	await page.getByRole('button', { name: 'Copy link at current time' }).click();

	await expect(page.getByRole('status')).toHaveText('Link copied');
	const copied = await page.evaluate(() => navigator.clipboard.readText());
	expect(copied).toBe(new URL('/tracks?play=track-1&t=95', page.url()).href);
});

test('no track loaded', async ({ page }) => {
	await page.goto('/tracks');

	await expect(page.getByRole('heading', { name: 'Tracks' })).toBeVisible();
	await expect(page.getByRole('button', { name: 'Copy link at current time' })).toHaveCount(0);
});

// --- Requirement: Persistent player bar -----------------------------------------------------

test('player bar appears when a track is played', async ({ page }) => {
	await page.goto('/tracks');
	await expect(playerBar(page)).toHaveCount(0);

	await playTrack(page, 'Knees Up');

	const bar = playerBar(page);
	await expect(bar).toBeVisible();
	await expect(bar.getByTestId('player-title')).toHaveText('Knees Up');
	await expect(bar.getByTestId('player-owner')).toHaveText('Alice Liddell');
	await expect(bar.getByTestId('elapsed')).toHaveText(/^\d+:\d\d$/);
	await expect(bar.getByTestId('duration')).toHaveText('3:27');
	// Fixed to the bottom of the viewport
	const box = await bar.boundingBox();
	expect(box.y + box.height).toBeCloseTo(page.viewportSize().height, 0);
});

test('playback continues across pages', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');
	await expect.poll(() => audioTime(page)).toBeGreaterThan(0.5);

	await page.getByRole('navigation').getByRole('link', { name: 'Home' }).click();

	await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome to Tune Lad !');
	await expect(playerBar(page)).toBeVisible();
	const before = await audioTime(page);
	await expect.poll(() => audioTime(page)).toBeGreaterThan(before + 0.5);
});

test('no player bar before playing', async ({ page }) => {
	await page.goto('/tracks');

	await expect(page.getByRole('heading', { name: 'Tracks' })).toBeVisible();
	await expect(playerBar(page)).toHaveCount(0);
});

// --- Requirement: Play and pause ------------------------------------------------------------

test('pause from the player bar', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');
	await expect.poll(() => audioTime(page)).toBeGreaterThan(0);

	await playerBar(page).getByRole('button', { name: 'Pause', exact: true }).click();

	await expect.poll(() => audio(page).evaluate((a) => a.paused)).toBe(true);
	await expect(playerBar(page).getByRole('button', { name: 'Play', exact: true })).toBeVisible();
});

test('resume from the player bar', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');
	await metadataLoaded(page);
	await playerBar(page).getByRole('button', { name: 'Pause', exact: true }).click();
	await audio(page).evaluate((a) => (a.currentTime = 30));

	await playerBar(page).getByRole('button', { name: 'Play', exact: true }).click();

	await expect.poll(() => audio(page).evaluate((a) => a.paused)).toBe(false);
	expect(await audioTime(page)).toBeGreaterThanOrEqual(30);
});

test('switch tracks from the list', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');

	await playTrack(page, 'Reel');

	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-2/play');
	await expect(playerBar(page).getByTestId('player-title')).toHaveText('Reel');
	await expect.poll(() => audioTime(page)).toBeGreaterThan(0);
});

// --- Requirement: Previous and next track ---------------------------------------------------

test('next track', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');

	await playerBar(page).getByRole('button', { name: 'Next track' }).click();

	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-2/play');
	await expect(playerBar(page).getByTestId('player-title')).toHaveText('Reel');
});

test('next is disabled on the last track', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Reel');

	await expect(playerBar(page).getByRole('button', { name: 'Next track' })).toBeDisabled();
});

test('previous restarts a track that has progressed', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Reel');
	await metadataLoaded(page);
	await audio(page).evaluate((a) => (a.currentTime = 20));
	await expect.poll(() => audioTime(page)).toBeGreaterThanOrEqual(20);

	await playerBar(page).getByRole('button', { name: 'Previous track' }).click();

	await expect.poll(() => audioTime(page)).toBeLessThan(3);
	expect(await loadedTrackSrc(page)).toBe('/api/tracks/track-2/play');
});

test('previous near the start loads the previous track', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Reel');

	await playerBar(page).getByRole('button', { name: 'Previous track' }).click();

	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-1/play');
});

test('track ends', async ({ page }) => {
	await page.goto('/tracks');

	await playTrack(page, 'Short Tune');

	// The one second tune ends, and the next track of the list plays
	await expect(audio(page)).toHaveAttribute('src', '/api/tracks/track-1/play', { timeout: 10_000 });
	await expect(playerBar(page).getByTestId('player-title')).toHaveText('Knees Up');
});

test('track opened from a link has no queue', async ({ page }) => {
	await page.goto('/tracks?play=track-1');

	await expect(playerBar(page).getByTestId('player-title')).toHaveText('Knees Up');
	await expect(playerBar(page).getByRole('button', { name: 'Next track' })).toBeDisabled();
});

// --- Requirement: Seek within the track -----------------------------------------------------

test('seek with the progress control', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');
	await metadataLoaded(page);

	await playerBar(page).getByRole('slider', { name: 'seek' }).fill('100');

	await expect.poll(() => audioTime(page)).toBeGreaterThanOrEqual(100);
	expect(await audioTime(page)).toBeLessThan(110);
});

// --- Requirement: Volume and mute -----------------------------------------------------------

test('mute', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');

	await playerBar(page).getByRole('button', { name: 'Mute', exact: true }).click();

	await expect.poll(() => audio(page).evaluate((a) => a.muted)).toBe(true);
	await expect(playerBar(page).getByRole('button', { name: 'Unmute' })).toBeVisible();
});

test('volume remembered', async ({ page }) => {
	await page.goto('/tracks');
	await playTrack(page, 'Knees Up');
	await playerBar(page).getByRole('slider', { name: 'volume' }).fill('0.3');
	await expect.poll(() => audio(page).evaluate((a) => a.volume)).toBeCloseTo(0.3);

	await page.reload();
	await playTrack(page, 'Knees Up');

	await expect.poll(() => audio(page).evaluate((a) => a.volume)).toBeCloseTo(0.3);
	await expect(playerBar(page).getByRole('slider', { name: 'volume' })).toHaveValue('0.3');
});
