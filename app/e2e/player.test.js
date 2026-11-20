import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// UI-only tests: the REST API is mocked with page.route, serving a real mp3 fixture and
// honouring single byte ranges like GET /api/tracks/{id}/play does. Nobody is logged in
// (GET /api/me answers 401), so these also cover the media spec's anonymous playback.
const MP3 = readFileSync(
	fileURLToPath(new URL('../../src/test/resources/mp3/Knees_Up.mp3', import.meta.url))
);
const TRACK = {
	id: 'track-1',
	title: 'Knees Up',
	description: 'A reel and a song',
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: 'account-alice', displayName: 'Alice Liddell' }
};

test.beforeEach(async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [TRACK] }));
	await page.route('**/api/tracks/*/play', (route) => {
		const range = /^bytes=(\d*)-(\d*)$/.exec(route.request().headers()['range'] ?? '');
		const headers = { 'Content-Type': 'audio/mpeg', 'Accept-Ranges': 'bytes' };
		if (!range) return route.fulfill({ status: 200, headers, body: MP3 });
		const first = range[1] ? Number(range[1]) : MP3.length - Number(range[2]);
		const last = range[1] && range[2] ? Math.min(Number(range[2]), MP3.length - 1) : MP3.length - 1;
		return route.fulfill({
			status: 206,
			headers: { ...headers, 'Content-Range': `bytes ${first}-${last}/${MP3.length}` },
			body: MP3.subarray(first, last + 1)
		});
	});
});

const audioTime = (page) => page.locator('#player').evaluate((audio) => audio.currentTime);

// --- Requirement: Start playback at a position from a link ---------------------------------

// Also the media spec's "Anonymous link with a start time": nobody is logged in
test('open a link with a start time', async ({ page }) => {
	await page.goto('/tracks?play=track-1&t=90');

	await expect(page.locator('#player')).toHaveAttribute('src', '/api/tracks/track-1/play#t=90');
	await expect.poll(() => audioTime(page)).toBeGreaterThanOrEqual(90);
});

test('open a link without a start time', async ({ page }) => {
	await page.goto('/tracks?play=track-1');

	await expect(page.locator('#player')).toHaveAttribute('src', '/api/tracks/track-1/play');
	await expect.poll(() => audioTime(page)).toBeLessThan(30);
});

test('open a link with an invalid start time', async ({ page }) => {
	await page.goto('/tracks?play=track-1&t=abc');

	await expect(page.locator('#player')).toHaveAttribute('src', '/api/tracks/track-1/play');
});

// --- Requirement: Copy a link to the current playback position -----------------------------

test('copy a link while a track is playing', async ({ page, context }) => {
	await context.grantPermissions(['clipboard-read', 'clipboard-write']);
	await page.goto('/tracks');
	await page.getByRole('button', { name: 'play track Knees Up button' }).click();

	const player = page.locator('#player');
	await expect.poll(() => player.evaluate((audio) => audio.readyState)).toBeGreaterThanOrEqual(1);
	await player.evaluate((audio) => {
		audio.pause();
		audio.currentTime = 95.7;
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
