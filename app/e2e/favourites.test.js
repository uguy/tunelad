import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/favourite (UI requirements).
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

/** Mocks the API for a logged-in alice whose favourites are `initial`, and records the favourite requests. */
async function loggedIn(page, initial = []) {
	const state = { favourites: [...initial], requests: [], refuse: false };
	await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
	await page.route('**/api/me/favourites', (route) => route.fulfill({ json: state.favourites }));
	await page.route('**/api/tracks/search*', (route) =>
		route.fulfill({ json: [KNEES_UP, MOSSY_BANKS] })
	);
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await page.route('**/api/tracks/*/favourite', (route) => {
		const request = route.request();
		state.requests.push(`${request.method()} ${new URL(request.url()).pathname}`);
		if (state.refuse) return route.fulfill({ status: 500 });
		const id = new URL(request.url()).pathname.split('/')[3];
		const known = [KNEES_UP, MOSSY_BANKS].find((t) => t.id === id);
		state.favourites = state.favourites.filter((t) => t.id !== id);
		if (request.method() === 'PUT') state.favourites.unshift(known);
		return route.fulfill({ status: 204 });
	});
	return state;
}

// --- Requirement: Favourites page -------------------------------------------------------------

test('favourites listed and playable', async ({ page }) => {
	await loggedIn(page, [MOSSY_BANKS, KNEES_UP]);
	await page.goto('/tracks/favourites');

	await expect(page.getByTestId('track-title')).toHaveText(['Mossy Banks', 'Knees Up']);
	await page.getByRole('button', { name: 'play track Mossy Banks button' }).click();
	await expect(page.getByTestId('player-title')).toHaveText('Mossy Banks');
});

test('empty state', async ({ page }) => {
	await loggedIn(page, []);
	await page.goto('/tracks/favourites');

	await expect(page.getByRole('heading', { name: 'No favourites yet' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Browse tracks' })).toHaveAttribute(
		'href',
		'/tracks'
	);
});

test('anonymous visitor', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/me/favourites', (route) => route.fulfill({ status: 401 }));
	await page.goto('/tracks/favourites');

	await expect(page.getByRole('link', { name: 'Log in' }).last()).toHaveAttribute('href', '/login');
	await expect(page.getByTestId('track-title')).toHaveCount(0);
});

// --- Requirement: Favourite control on tracks -------------------------------------------------

test('mark and unmark from the list', async ({ page }) => {
	const api = await loggedIn(page, []);
	await page.goto('/tracks');
	const heart = page.getByRole('button', { name: 'favourite track Knees Up button' });
	await expect(heart).toHaveAttribute('aria-pressed', 'false');

	await heart.click();
	await expect(heart).toHaveAttribute('aria-pressed', 'true');
	await heart.click();
	await expect(heart).toHaveAttribute('aria-pressed', 'false');

	expect(api.requests).toEqual([
		'PUT /api/tracks/track-1/favourite',
		'DELETE /api/tracks/track-1/favourite'
	]);
});

test('hearts reflect the favourites the server has', async ({ page }) => {
	await loggedIn(page, [MOSSY_BANKS]);
	await page.goto('/tracks');

	await expect(
		page.getByRole('button', { name: 'favourite track Mossy Banks button' })
	).toHaveAttribute('aria-pressed', 'true');
	await expect(
		page.getByRole('button', { name: 'favourite track Knees Up button' })
	).toHaveAttribute('aria-pressed', 'false');
});

test('the player bar has a heart for the loaded track', async ({ page }) => {
	const api = await loggedIn(page, []);
	await page.goto('/tracks');
	await page.getByRole('button', { name: 'play track Knees Up button' }).click();

	const bar = page.getByRole('region', { name: 'player' });
	await bar.getByRole('button', { name: /favourite track/ }).click();

	await expect(bar.getByRole('button', { name: /favourite track/ })).toHaveAttribute(
		'aria-pressed',
		'true'
	);
	expect(api.requests).toEqual(['PUT /api/tracks/track-1/favourite']);
});

test('a refused change goes back and says so', async ({ page }) => {
	const api = await loggedIn(page, []);
	api.refuse = true;
	await page.goto('/tracks');
	const heart = page.getByRole('button', { name: 'favourite track Knees Up button' });

	await heart.click();

	await expect(page.getByText('Could not add this track to your favourites.')).toBeVisible();
	await expect(heart).toHaveAttribute('aria-pressed', 'false');
});

test('no favourite control for an anonymous visitor', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/me/favourites', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [KNEES_UP] }));
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await page.goto('/tracks');

	await expect(page.getByTestId('track-title')).toHaveText('Knees Up');
	await expect(page.getByRole('button', { name: /favourite track/ })).toHaveCount(0);
});
