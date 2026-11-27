import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/playlist (UI requirements).
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const track = (id, title) => ({
	id,
	title,
	description: `${title}, described`,
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: ALICE
});
const KNEES_UP = track('track-1', 'Knees Up');
const MOSSY_BANKS = track('track-2', 'Mossy Banks');
const SLOW_AIR = track('track-3', 'Slow Air');
const ALL = [KNEES_UP, MOSSY_BANKS, SLOW_AIR];

const summary = (p) => ({
	id: p.id,
	name: p.name,
	description: p.description ?? null,
	trackCount: p.tracks.length,
	updatedAt: '2026-11-27T10:00:00Z'
});

/**
 * Mocks the API for a logged-in alice who owns `playlists` (objects with id, name, tracks), keeps them as the
 * requests change them, and records every playlist request as "METHOD path body".
 */
async function loggedIn(page, initial) {
	const state = {
		playlists: structuredClone(initial),
		requests: [],
		// A status the next write answers with, instead of success
		refuse: null
	};
	await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
	await page.route('**/api/me/favourites', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: ALL }));
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await page.route('**/api/playlists**', (route) => {
		const request = route.request();
		const path = new URL(request.url()).pathname;
		const method = request.method();
		const body = request.postData();
		if (method !== 'GET') {
			state.requests.push(`${method} ${path}${body ? ' ' + body : ''}`);
			if (state.refuse) return route.fulfill({ status: state.refuse });
		}
		const parts = path.split('/').slice(3); // after /api/playlists
		const playlist = state.playlists.find((p) => p.id === parts[0]);
		if (parts.length === 0) {
			if (method === 'POST') {
				const created = { id: `p${state.playlists.length + 1}`, ...JSON.parse(body), tracks: [] };
				state.playlists.unshift(created);
				return route.fulfill({ status: 201, json: summary(created) });
			}
			return route.fulfill({ json: state.playlists.map(summary) });
		}
		if (!playlist) return route.fulfill({ status: 404 });
		if (parts.length === 1) {
			if (method === 'DELETE') {
				state.playlists = state.playlists.filter((p) => p !== playlist);
				return route.fulfill({ status: 204 });
			}
			if (method === 'PUT') Object.assign(playlist, JSON.parse(body));
			return route.fulfill({
				json:
					method === 'PUT' ? summary(playlist) : { ...summary(playlist), tracks: playlist.tracks }
			});
		}
		if (parts[1] === 'tracks' && parts.length === 2) {
			const ids = JSON.parse(body);
			playlist.tracks = ids.map((id) => playlist.tracks.find((t) => t.id === id));
			return route.fulfill({ status: 204 });
		}
		if (parts[1] === 'tracks' && method === 'PUT') {
			playlist.tracks.push(ALL.find((t) => t.id === parts[2]));
		} else if (parts[1] === 'tracks') {
			playlist.tracks = playlist.tracks.filter((t) => t.id !== parts[2]);
		}
		return route.fulfill({ status: 204 });
	});
	return state;
}

const titles = (page) => page.getByTestId('track-title');

// --- Requirement: Playlists pages ---------------------------------------------------------------

test('playlists listed', async ({ page }) => {
	await loggedIn(page, [
		{ id: 'p1', name: 'Session tunes', tracks: [KNEES_UP, MOSSY_BANKS, SLOW_AIR] }
	]);
	await page.goto('/tracks/playlists');

	const link = page.getByRole('link', { name: /Session tunes/ });
	await expect(link).toContainText('3 tracks');
	await expect(link).toHaveAttribute('href', '/tracks/playlists/p1');
});

test('create from the list', async ({ page }) => {
	const api = await loggedIn(page, []);
	await page.goto('/tracks/playlists');

	await page.getByLabel('Name of the new playlist').fill('Reels');
	await page.getByRole('button', { name: 'Create playlist' }).click();

	expect(api.requests[0]).toBe('POST /api/playlists {"name":"Reels","description":null}');
	// The new playlist's page opens
	await expect(page).toHaveURL(/\/tracks\/playlists\/p1$/);
	await expect(page.getByRole('heading', { name: 'Reels' })).toBeVisible();
});

test('empty list of playlists', async ({ page }) => {
	await loggedIn(page, []);
	await page.goto('/tracks/playlists');

	await expect(page.getByRole('heading', { name: 'No playlists yet' })).toBeVisible();
});

test('anonymous visitor', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/playlists**', (route) => route.fulfill({ status: 401 }));
	await page.goto('/tracks/playlists');

	await expect(page.getByRole('link', { name: 'Log in' }).last()).toHaveAttribute('href', '/login');
	await expect(page.getByLabel('Name of the new playlist')).toHaveCount(0);
});

test('empty playlist', async ({ page }) => {
	await loggedIn(page, [{ id: 'p1', name: 'Session tunes', tracks: [] }]);
	await page.goto('/tracks/playlists/p1');

	await expect(page.getByRole('heading', { name: 'This playlist is empty' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Tracks page' })).toHaveAttribute('href', '/tracks');
});

test('unknown playlist', async ({ page }) => {
	await loggedIn(page, []);
	await page.goto('/tracks/playlists/nope');

	await expect(page.getByRole('heading', { name: 'Playlist not found' })).toBeVisible();
});

test('rename', async ({ page }) => {
	const api = await loggedIn(page, [{ id: 'p1', name: 'Session tunes', tracks: [KNEES_UP] }]);
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'Rename' }).click();
	await page.getByLabel('Name', { exact: true }).fill('Reels');
	await page.getByRole('button', { name: 'Save' }).click();

	await expect(page.getByRole('heading', { name: 'Reels' })).toBeVisible();
	expect(api.requests).toContain('PUT /api/playlists/p1 {"name":"Reels","description":null}');
});

test('delete the playlist', async ({ page }) => {
	const api = await loggedIn(page, [{ id: 'p1', name: 'Session tunes', tracks: [KNEES_UP] }]);
	page.on('dialog', (dialog) => dialog.accept());
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'delete playlist Session tunes' }).click();

	await expect(page).toHaveURL(/\/tracks\/playlists$/);
	expect(api.requests).toContain('DELETE /api/playlists/p1');
});

test('remove a track', async ({ page }) => {
	const api = await loggedIn(page, [
		{ id: 'p1', name: 'Session tunes', tracks: [KNEES_UP, MOSSY_BANKS] }
	]);
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'Remove Knees Up from the playlist' }).click();

	await expect(titles(page)).toHaveText(['Mossy Banks']);
	expect(api.requests).toEqual(['DELETE /api/playlists/p1/tracks/track-1']);
});

// --- Requirement: Reorder a playlist by drag and drop ------------------------------------------

const THREE = [{ id: 'p1', name: 'Session tunes', tracks: [KNEES_UP, MOSSY_BANKS, SLOW_AIR] }];
const row = (page, id) => page.getByTestId(`playlist-track-${id}`);

test('move a track down with the button', async ({ page }) => {
	const api = await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'Move Knees Up down' }).click();

	await expect(titles(page)).toHaveText(['Mossy Banks', 'Knees Up', 'Slow Air']);
	expect(api.requests).toEqual(['PUT /api/playlists/p1/tracks ["track-2","track-1","track-3"]']);
});

test('the buttons work from the keyboard', async ({ page }) => {
	const api = await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'Move Slow Air up' }).focus();
	await page.keyboard.press('Enter');

	await expect(titles(page)).toHaveText(['Knees Up', 'Slow Air', 'Mossy Banks']);
	expect(api.requests).toHaveLength(1);
});

test('the first track cannot move up and the last cannot move down', async ({ page }) => {
	await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	await expect(page.getByRole('button', { name: 'Move Knees Up up' })).toBeDisabled();
	await expect(page.getByRole('button', { name: 'Move Slow Air down' })).toBeDisabled();
});

test('drag a track down', async ({ page }) => {
	const api = await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	// Dropped on the lower half of the last row: it goes below it
	const last = row(page, 'track-3');
	const box = await last.boundingBox();
	await row(page, 'track-1').dragTo(last, { targetPosition: { x: 40, y: box.height - 4 } });

	await expect(titles(page)).toHaveText(['Mossy Banks', 'Slow Air', 'Knees Up']);
	expect(api.requests).toEqual(['PUT /api/playlists/p1/tracks ["track-2","track-3","track-1"]']);
});

test('drag a track up', async ({ page }) => {
	const api = await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	await row(page, 'track-3').dragTo(row(page, 'track-1'), { targetPosition: { x: 40, y: 4 } });

	await expect(titles(page)).toHaveText(['Slow Air', 'Knees Up', 'Mossy Banks']);
	expect(api.requests).toEqual(['PUT /api/playlists/p1/tracks ["track-3","track-1","track-2"]']);
});

test('dropping a track where it was sends nothing', async ({ page }) => {
	const api = await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	const first = row(page, 'track-1');
	const box = await first.boundingBox();
	await first.dragTo(first, { targetPosition: { x: 40, y: box.height / 2 } });
	// Just above the row below it is where it already is
	await first.dragTo(row(page, 'track-2'), { targetPosition: { x: 40, y: 4 } });

	await expect(titles(page)).toHaveText(['Knees Up', 'Mossy Banks', 'Slow Air']);
	expect(api.requests).toEqual([]);
});

test('a failed save goes back to the previous order and says so', async ({ page }) => {
	const api = await loggedIn(page, THREE);
	api.refuse = 500;
	await page.goto('/tracks/playlists/p1');

	await row(page, 'track-3').dragTo(row(page, 'track-1'), { targetPosition: { x: 40, y: 4 } });

	await expect(page.getByText('Could not save the new order.')).toBeVisible();
	await expect(titles(page)).toHaveText(['Knees Up', 'Mossy Banks', 'Slow Air']);
});

// --- Requirement: Play a playlist --------------------------------------------------------------

test('play all, then the next track', async ({ page }) => {
	await loggedIn(page, [{ id: 'p1', name: 'Reels', tracks: [MOSSY_BANKS, KNEES_UP] }]);
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'Play', exact: true }).click();
	await expect(page.getByTestId('player-title')).toHaveText('Mossy Banks');

	await page.getByRole('button', { name: 'Next track' }).click();
	await expect(page.getByTestId('player-title')).toHaveText('Knees Up');
});

test('play from a track', async ({ page }) => {
	await loggedIn(page, THREE);
	await page.goto('/tracks/playlists/p1');

	await page.getByRole('button', { name: 'play track Mossy Banks button' }).click();

	await expect(page.getByTestId('player-title')).toHaveText('Mossy Banks');
});

// --- Requirement: Add a track to a playlist from the track list --------------------------------

test('add a track from the list', async ({ page }) => {
	const api = await loggedIn(page, [{ id: 'p1', name: 'Session tunes', tracks: [] }]);
	await page.goto('/tracks');

	await page.getByRole('button', { name: 'add track Knees Up to a playlist button' }).click();
	await page.getByRole('menuitem', { name: /Session tunes/ }).click();

	expect(api.requests).toEqual(['PUT /api/playlists/p1/tracks/track-1']);
	await expect(page.getByText('Added “Knees Up” to Session tunes.')).toBeVisible();
});

test('create a playlist from the add menu', async ({ page }) => {
	const api = await loggedIn(page, []);
	await page.goto('/tracks');

	await page.getByRole('button', { name: 'add track Knees Up to a playlist button' }).click();
	await page.getByRole('menuitem', { name: 'New playlist' }).click();
	await page.getByLabel('Name of the new playlist').fill('Reels');
	await page.getByRole('button', { name: 'Create' }).click();

	await expect(page.getByText('Added “Knees Up” to Reels.')).toBeVisible();
	expect(api.requests).toEqual([
		'POST /api/playlists {"name":"Reels","description":null}',
		'PUT /api/playlists/p1/tracks/track-1'
	]);
});

test('no add to playlist control for an anonymous visitor', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [KNEES_UP] }));
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await page.goto('/tracks');

	await expect(titles(page)).toHaveText('Knees Up');
	await expect(page.getByRole('button', { name: /to a playlist/ })).toHaveCount(0);
});
