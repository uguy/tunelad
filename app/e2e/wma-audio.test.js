import { expect, test } from '@playwright/test';

// UI-only tests for WMA support (specs track and media): the REST API is mocked with page.route, and the browser's
// EventSource is replaced by one the test drives, as there is no server behind these pages.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };

const WMA_TRACK = {
	id: 'track-wma',
	title: 'Touching Cloth',
	description: 'Three tunes',
	artist: 'Dervish',
	album: null,
	format: 'WMA',
	tags: [],
	owner: { id: ALICE.id, displayName: ALICE.displayName }
};
const COOLEYS = { id: 'tune-1', name: "Cooley's", aliases: [], danceType: 'reel' };
const REEL = { id: 'set-1', tunes: [COOLEYS] };
const WMA_FILE = {
	name: 'touching-cloth.wma',
	mimeType: 'audio/x-ms-wma',
	buffer: Buffer.alloc(64 * 1024)
};

/** Replaces EventSource with a fake that opens at once, and gives the test `window.__live` to send events. */
async function fakeLiveStream(page) {
	await page.addInitScript(() => {
		const sources = [];
		class FakeEventSource {
			constructor(url) {
				this.url = url;
				this.readyState = 0;
				this.listeners = {};
				sources.push(this);
				setTimeout(() => {
					this.readyState = 1;
					this.onopen?.();
				}, 0);
			}
			addEventListener(type, listener) {
				(this.listeners[type] ??= []).push(listener);
			}
			close() {
				this.readyState = 2;
			}
		}
		window.EventSource = FakeEventSource;
		window.__live = {
			opened: () => sources.length,
			emit: (type, id) =>
				sources.at(-1)?.listeners[type]?.forEach((l) => l({ data: JSON.stringify({ type, id }) }))
		};
	});
}

/** Mocks who is logged in, the library, the sets and the audio state (`server.audio` says what the server answers). */
async function mockApi(page, context, tracks) {
	const server = { audio: 'PENDING', uploads: [] };
	await fakeLiveStream(page);
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'PENDING', peaks: [] } })
	);
	await page.route('**/api/tracks/*/audio', (route) =>
		route.fulfill({ json: { status: server.audio } })
	);
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: tracks }));
	await page.route('**/api/sets?*', (route) => route.fulfill({ json: [REEL] }));
	await page.route('**/api/tunes?*', (route) => route.fulfill({ json: [COOLEYS] }));
	await page.route('**/api/tracks', (route) => {
		if (route.request().method() !== 'POST') return route.fallback();
		server.uploads.push(route.request());
		return route.fulfill({
			status: 201,
			json: WMA_TRACK,
			headers: { Location: '/api/tracks/track-wma' }
		});
	});
	return server;
}

const playButton = (page) => page.getByRole('button', { name: 'play track Touching Cloth button' });

test('a wma file is accepted by the upload form and the track waits for its audio', async ({
	page,
	context
}) => {
	const server = await mockApi(page, context, []);
	await page.goto('/tracks/upload');
	await page.getByLabel('Title').fill('Touching Cloth');
	await page.getByLabel('Description').fill('Three tunes');
	await page.getByRole('combobox', { name: 'Set' }).selectOption({ label: "Cooley's" });

	await page.getByLabel(/Audio file/).setInputFiles(WMA_FILE);
	await expect(page.getByRole('alert')).toHaveCount(0);
	await page.getByRole('button', { name: 'Upload' }).click();

	await expect(page).toHaveURL('/tracks');
	expect(server.uploads).toHaveLength(1);
	await expect(page.getByTestId('track-title')).toHaveText(['Touching Cloth']);
	await expect(page.getByRole('status').filter({ hasText: 'Preparing audio' })).toBeVisible();
	await expect(playButton(page)).toBeDisabled();
});

test('the track becomes playable, without a reload, once the server says its audio is prepared', async ({
	page,
	context
}) => {
	const server = await mockApi(page, context, [WMA_TRACK]);
	await page.goto('/tracks');
	await page.waitForFunction(() => window.__live?.opened() === 1);
	await expect(page.getByRole('status').filter({ hasText: 'Preparing audio' })).toBeVisible();
	await expect(playButton(page)).toBeDisabled();
	await page.evaluate(() => (window.__notReloaded = true));

	server.audio = 'READY';
	await page.evaluate(() => window.__live.emit('audio.prepared', 'track-wma'));

	await expect(playButton(page)).toBeEnabled();
	await expect(page.getByTestId('audio-status')).toHaveCount(0);
	expect(await page.evaluate(() => window.__notReloaded)).toBe(true);
});

test('a conversion that failed is said in text, and the track can still be edited and deleted', async ({
	page,
	context
}) => {
	const server = await mockApi(page, context, [WMA_TRACK]);
	server.audio = 'FAILED';

	await page.goto('/tracks');

	await expect(
		page.getByRole('status').filter({ hasText: 'Audio could not be prepared' })
	).toBeVisible();
	await expect(playButton(page)).toBeDisabled();
	await expect(page.getByRole('button', { name: 'edit track Touching Cloth' })).toBeVisible();
	await expect(page.getByRole('button', { name: 'delete track Touching Cloth' })).toBeVisible();
});
