import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route; GET /api/me answers 401 to anonymous visitors.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const TRACK = {
	id: 'track-1',
	title: 'Knees Up',
	description: 'A reel and a song',
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: ALICE
};

const anonymous = (route) => route.fulfill({ status: 401 });
const loggedInAsAlice = (route) => route.fulfill({ json: ALICE });

test.beforeEach(async ({ page }) => {
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [TRACK] }));
	// Waveforms are not under test here: an empty one, as for a track whose waveform failed
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
});

// --- Requirement: Login state in the header --------------------------------------------------

test('anonymous visitor', async ({ page }) => {
	await page.route('**/api/me', anonymous);
	await page.goto('/');

	await expect(page.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
	await expect(page.getByRole('button', { name: 'Log out' })).toHaveCount(0);
});

test('logged-in user', async ({ page }) => {
	await page.route('**/api/me', loggedInAsAlice);
	await page.goto('/');

	// The mobile menu repeats it, hidden at this width
	await expect(
		page.getByRole('banner').getByText('Alice Liddell').filter({ visible: true })
	).toBeVisible();
	await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Log in' })).toHaveCount(0);
});

test('log out from the header', async ({ page, context }) => {
	await page.route('**/api/me', loggedInAsAlice);
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	let logout;
	await page.route('**/logout', (route) => {
		logout = route.request();
		return route.fulfill({ status: 200, contentType: 'text/html', body: '<p>Logged out</p>' });
	});
	await page.goto('/');

	await page.getByRole('button', { name: 'Log out' }).click();

	await expect(page.getByText('Logged out')).toBeVisible();
	expect(logout.method()).toBe('POST');
	expect(logout.postData()).toContain('_csrf=csrf-123');
});

// --- Requirement: Show who uploaded each track (track spec) ----------------------------------

test('uploader shown in the list', async ({ page }) => {
	await page.route('**/api/me', anonymous);
	await page.goto('/tracks');

	await expect(page.getByTestId('track-owner')).toHaveText('Alice Liddell');
});
