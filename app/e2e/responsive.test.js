import { expect, test } from '@playwright/test';

// --- Requirement: Responsive layout (appearance spec) -------------------------------------------------------

const TRACK = {
	id: 'track-1',
	title:
		'A track with a rather long title that must wrap or truncate instead of pushing the page wide',
	description: 'A reel and a song',
	artist: null,
	album: null,
	format: 'MP3',
	tags: ['reel', 'song', 'a-tag-with-a-long-name'],
	owner: { id: 'account-alice', displayName: 'Alice Liddell', admin: false }
};

test.beforeEach(async ({ page }) => {
	await page.setViewportSize({ width: 360, height: 740 });
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/login-providers', (route) =>
		route.fulfill({ json: [{ id: 'keycloak', name: 'Keycloak' }] })
	);
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [TRACK] }));
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
});

for (const path of ['/', '/tracks', '/about', '/login']) {
	test(`${path} does not scroll horizontally at 360 px`, async ({ page }) => {
		await page.goto(path);
		await expect(page.getByRole('button', { name: 'Open main menu' })).toBeVisible();
		const overflow = await page.evaluate(
			() => document.documentElement.scrollWidth - document.documentElement.clientWidth
		);
		expect(overflow).toBeLessThanOrEqual(0);
	});
}

test('the navigation is in a menu button on a phone', async ({ page }) => {
	await page.goto('/tracks');
	await expect(page.getByRole('banner').getByRole('link', { name: 'About' })).toBeHidden();
	await page.getByRole('button', { name: 'Open main menu' }).click();
	await expect(page.getByRole('banner').getByRole('link', { name: 'About' })).toBeVisible();
});

test('the player bar does not cover the end of the page', async ({ page }) => {
	await page.goto('/tracks?play=track-1');
	await expect(page.getByRole('region', { name: 'player' })).toBeVisible();
	await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
	const footer = page.getByRole('contentinfo');
	const bar = await page.getByRole('region', { name: 'player' }).boundingBox();
	const end = await footer.boundingBox();
	expect(end.y + end.height).toBeLessThanOrEqual(bar.y + 1);
});
