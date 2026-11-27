import { expect, test } from '@playwright/test';

// --- Requirement: Collections navigation (appearance spec) -------------------------------------------------

test.beforeEach(async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [] }));
});

test('the collections navigation has Favourites and Playlists only', async ({ page }) => {
	await page.goto('/tracks');

	const nav = page.getByRole('navigation', { name: 'collections' });
	await expect(nav.getByRole('link')).toHaveText(['Favourites', 'Playlists']);
	await expect(nav.getByRole('link', { name: 'Favourites' })).toHaveAttribute(
		'href',
		'/tracks/favourites'
	);
	await expect(nav.getByRole('link', { name: 'Playlists' })).toHaveAttribute(
		'href',
		'/tracks/playlists'
	);
	await expect(page.getByRole('link', { name: 'Albums' })).toHaveCount(0);
});

test('the albums page is gone', async ({ page }) => {
	await page.goto('/tracks/albums');

	await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});
