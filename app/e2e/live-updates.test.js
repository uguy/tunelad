import { expect, test } from '@playwright/test';

// UI-only tests for the live update requirements: the REST API is mocked with page.route, and the browser's
// EventSource is replaced by one the test drives, as there is no server behind these pages.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };

const track = (id, title, owner = 'Bob Smith') => ({
	id,
	title,
	description: `${title}, described`,
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: `account-${owner.toLowerCase()}`, displayName: owner }
});
const KNEES_UP = track('track-1', 'Knees Up');
const MOSSY_BANKS = track('track-2', 'Mossy Banks');

const PEAKS = Array.from({ length: 200 }, (_, i) => (i % 10) / 10);

/**
 * Replaces EventSource with a fake that opens at once, and gives the test `window.__live` to send events, fail,
 * reconnect and count the connections the page opened.
 */
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
				this.closed = true;
				this.readyState = 2;
			}
		}
		window.EventSource = FakeEventSource;
		const current = () => sources.filter((source) => !source.closed).at(-1);
		window.__live = {
			opened: () => sources.length,
			emit: (type, id) =>
				current()?.listeners[type]?.forEach((listener) =>
					listener({ data: JSON.stringify({ type, id }) })
				),
			fail: () => {
				const source = current();
				source.readyState = 0;
				source.onerror?.();
			},
			reconnect: () => {
				const source = current();
				source.readyState = 1;
				source.onopen?.();
			}
		};
	});
}

const emit = (page, type, id) => page.evaluate(([t, i]) => window.__live.emit(t, i), [type, id]);

/**
 * Mocks who is logged in, the waveforms (`waveform.current` says what the server answers) and the search (`library`
 * holds what it finds), and opens the tracks page once its live connection is open.
 */
async function openTracks(page, { account = ALICE, tracks = [KNEES_UP, MOSSY_BANKS] } = {}) {
	const server = {
		library: tracks,
		waveform: { status: 'PENDING', peaks: [] },
		searches: [],
		waveformRequests: 0
	};
	await fakeLiveStream(page);
	await page.route('**/api/me', (route) =>
		account ? route.fulfill({ json: account }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/tracks/*/waveform', (route) => {
		server.waveformRequests++;
		return route.fulfill({ json: server.waveform });
	});
	await page.route('**/api/tracks/search*', (route) => {
		server.searches.push(new URL(route.request().url()).searchParams.get('q'));
		return route.fulfill({ json: server.library });
	});
	await page.goto('/tracks');
	await expect(page.getByText(tracks[0].title)).toBeVisible();
	await page.waitForFunction(() => window.__live?.opened() === 1);
	return server;
}

test.describe('waveforms', () => {
	test('a waveform appears without a reload once it is computed', async ({ page }) => {
		const server = await openTracks(page, { tracks: [KNEES_UP] });
		await expect(page.getByTestId('waveform-placeholder')).toBeVisible();
		await page.evaluate(() => (window.__notReloaded = true));

		server.waveform = { status: 'READY', peaks: PEAKS };
		await emit(page, 'waveform.computed', KNEES_UP.id);

		await expect(page.getByTestId('waveform')).toBeVisible();
		await expect(page.getByTestId('waveform-placeholder')).toHaveCount(0);
		expect(await page.evaluate(() => window.__notReloaded)).toBe(true);
	});

	test('a waveform computed long after the page was opened still appears', async ({ page }) => {
		const server = await openTracks(page, { tracks: [KNEES_UP] });
		// The polling this replaced gave up after 30 seconds; the placeholder just stays until the server says so
		await page.waitForTimeout(3500);
		expect(server.waveformRequests).toBe(1);

		server.waveform = { status: 'READY', peaks: PEAKS };
		await emit(page, 'waveform.computed', KNEES_UP.id);

		await expect(page.getByTestId('waveform')).toBeVisible();
	});

	test('a waveform that failed shows no waveform and no error', async ({ page }) => {
		const server = await openTracks(page, { tracks: [KNEES_UP] });

		server.waveform = { status: 'FAILED', peaks: [] };
		await emit(page, 'waveform.computed', KNEES_UP.id);

		await expect.poll(() => server.waveformRequests).toBe(2);
		await expect(page.getByTestId('waveform-placeholder')).toBeVisible();
	});
});

test.describe('the tracks list', () => {
	test("shows another user's new track", async ({ page }) => {
		const server = await openTracks(page);
		const hornpipe = track('track-3', 'Hornpipe Set', 'Carol');

		server.library = [hornpipe, KNEES_UP, MOSSY_BANKS];
		await emit(page, 'track.indexed', hornpipe.id);

		await expect(page.getByText('Hornpipe Set')).toBeVisible();
		await expect(page.getByText('Hornpipe Set')).toHaveCount(1);
	});

	test("shows another user's rename", async ({ page }) => {
		const server = await openTracks(page);

		server.library = [track('track-2', 'Mossy Banks (live)'), KNEES_UP];
		await emit(page, 'track.indexed', MOSSY_BANKS.id);

		await expect(page.getByText('Mossy Banks (live)')).toBeVisible();
		await expect(page.getByText('Mossy Banks', { exact: true })).toHaveCount(0);
	});

	test('drops a track another user deleted', async ({ page }) => {
		const server = await openTracks(page);

		server.library = [KNEES_UP];
		await emit(page, 'track.unindexed', MOSSY_BANKS.id);

		await expect(page.getByText('Mossy Banks')).toHaveCount(0);
		await expect(page.getByText('Knees Up')).toBeVisible();
	});

	test('an anonymous visitor sees it too', async ({ page }) => {
		const server = await openTracks(page, { account: null });
		const hornpipe = track('track-3', 'Hornpipe Set', 'Carol');

		server.library = [hornpipe, KNEES_UP, MOSSY_BANKS];
		await emit(page, 'track.indexed', hornpipe.id);

		await expect(page.getByText('Hornpipe Set')).toBeVisible();
	});

	test('a burst of events makes one request', async ({ page }) => {
		const server = await openTracks(page);
		server.searches.length = 0;

		for (let i = 0; i < 10; i++) await emit(page, 'track.indexed', `track-${i}`);

		await expect.poll(() => server.searches.length).toBe(1);
		await page.waitForTimeout(600);
		expect(server.searches).toEqual(['']);
	});

	test('refetches after the connection was lost and came back', async ({ page }) => {
		const server = await openTracks(page);
		const hornpipe = track('track-3', 'Hornpipe Set', 'Carol');

		await page.evaluate(() => window.__live.fail());
		server.library = [hornpipe, KNEES_UP, MOSSY_BANKS];
		await page.evaluate(() => window.__live.reconnect());

		await expect(page.getByText('Hornpipe Set')).toBeVisible();
	});

	test('keeps the list when the refetch fails', async ({ page }) => {
		await openTracks(page);
		await page.unroute('**/api/tracks/search*');
		await page.route('**/api/tracks/search*', (route) => route.fulfill({ status: 500 }));

		await emit(page, 'track.indexed', 'track-3');
		await page.waitForTimeout(600);

		await expect(page.getByText('Knees Up')).toBeVisible();
		await expect(page.getByText('Mossy Banks')).toBeVisible();
	});
});

test.describe('the live connection', () => {
	test('is one per tab, however much the user navigates', async ({ page }) => {
		await openTracks(page);

		await page.getByRole('link', { name: 'About' }).first().click();
		await expect(page).toHaveURL(/\/about/);
		await page.goBack();
		await expect(page.getByText('Knees Up')).toBeVisible();

		expect(await page.evaluate(() => window.__live.opened())).toBe(1);
	});
});

test.describe('favourites and playlists across tabs', () => {
	const summary = (id, name, trackCount) => ({
		id,
		name,
		description: null,
		trackCount,
		updatedAt: '2026-11-27T10:00:00Z'
	});

	/** Mocks alice's favourites and playlists, which the test changes, and opens the page with a live stream. */
	async function open(page, path) {
		const server = { favourites: [KNEES_UP], playlists: [summary('p1', 'Session tunes', 0)] };
		await fakeLiveStream(page);
		await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
		await page.route('**/api/me/favourites', (route) => route.fulfill({ json: server.favourites }));
		await page.route('**/api/playlists', (route) => route.fulfill({ json: server.playlists }));
		await page.route('**/api/playlists/p1', (route) =>
			route.fulfill({ json: { ...server.playlists[0], tracks: [] } })
		);
		await page.route('**/api/tracks/search*', (route) =>
			route.fulfill({ json: [KNEES_UP, MOSSY_BANKS] })
		);
		await page.route('**/api/tracks/*/waveform', (route) =>
			route.fulfill({ json: { status: 'FAILED', peaks: [] } })
		);
		await page.goto(path);
		await page.waitForFunction(() => window.__live?.opened() === 1);
		return server;
	}

	test('a favourite added in another tab appears without a reload', async ({ page }) => {
		const server = await open(page, '/tracks/favourites');
		await expect(page.getByTestId('track-title')).toHaveText(['Knees Up']);
		await page.evaluate(() => (window.__notReloaded = true));

		server.favourites = [MOSSY_BANKS, KNEES_UP];
		await emit(page, 'favourite.changed', MOSSY_BANKS.id);

		await expect(page.getByTestId('track-title')).toHaveText(['Mossy Banks', 'Knees Up']);
		expect(await page.evaluate(() => window.__notReloaded)).toBe(true);
	});

	test('the heart of a track follows a favourite changed in another tab', async ({ page }) => {
		const server = await open(page, '/tracks');
		await expect(
			page.getByRole('button', { name: 'favourite track Mossy Banks button' })
		).toHaveAttribute('aria-pressed', 'false');

		server.favourites = [KNEES_UP, MOSSY_BANKS];
		await emit(page, 'favourite.changed', MOSSY_BANKS.id);

		await expect(
			page.getByRole('button', { name: 'favourite track Mossy Banks button' })
		).toHaveAttribute('aria-pressed', 'true');
	});

	test('the playlist list follows a playlist created in another tab', async ({ page }) => {
		const server = await open(page, '/tracks/playlists');
		await expect(page.getByRole('link', { name: /Session tunes/ })).toBeVisible();

		server.playlists = [summary('p2', 'Reels', 0), ...server.playlists];
		await emit(page, 'playlist.changed', 'p2');

		await expect(page.getByRole('link', { name: /Reels/ })).toBeVisible();
	});

	test('a playlist open in a tab follows its changes in another', async ({ page }) => {
		const server = await open(page, '/tracks/playlists/p1');
		await expect(page.getByRole('heading', { name: 'Session tunes' })).toBeVisible();

		server.playlists = [summary('p1', 'Renamed elsewhere', 0)];
		await emit(page, 'playlist.changed', 'p1');

		await expect(page.getByRole('heading', { name: 'Renamed elsewhere' })).toBeVisible();
	});

	test('the account counts follow the changes', async ({ page }) => {
		const server = await open(page, '/account');
		await expect(page.getByRole('link', { name: /1 favourite/ })).toBeVisible();

		server.favourites = [KNEES_UP, MOSSY_BANKS];
		await emit(page, 'favourite.changed', MOSSY_BANKS.id);

		await expect(page.getByRole('link', { name: /2 favourites/ })).toBeVisible();
	});

	test('everything is fetched again when the connection comes back', async ({ page }) => {
		const server = await open(page, '/tracks/favourites');
		await expect(page.getByTestId('track-title')).toHaveText(['Knees Up']);

		server.favourites = [MOSSY_BANKS];
		await page.evaluate(() => window.__live.fail());
		await page.evaluate(() => window.__live.reconnect());

		await expect(page.getByTestId('track-title')).toHaveText(['Mossy Banks']);
	});
});
