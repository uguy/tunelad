import { expect, test } from '@playwright/test';

// --- Requirement: Light and dark themes in one palette (appearance spec) -------------------------------------
// The API is mocked; every page is opened in the dark theme, and no panel may stay light.

const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const TRACK = {
	id: 'track-1',
	title: 'Knees Up',
	description: 'A reel and a song',
	artist: 'Breabach',
	album: 'Frontier',
	format: 'MP3',
	tags: ['reel'],
	owner: ALICE
};

const PAGES = ['/', '/tracks', '/tracks/upload', '/login', '/about'];

test.beforeEach(async ({ page }) => {
	await page.addInitScript(() => localStorage.setItem('tunelad.theme', 'dark'));
	await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
	await page.route('**/api/login-providers', (route) =>
		route.fulfill({ json: [{ id: 'keycloak', name: 'Keycloak' }] })
	);
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [TRACK] }));
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await page.route('**/actuator/info', (route) =>
		route.fulfill({
			json: {
				app: { name: 'Tune Lad', description: 'A playground' },
				build: { version: '1', time: '2026-01-01T00:00:00Z' },
				git: {
					branch: 'main',
					commit: { id: { abbrev: 'abc' } },
					remote: { origin: { url: 'x' } }
				},
				os: {}
			}
		})
	);
	await page.route('**/actuator/health', (route) =>
		route.fulfill({
			json: { status: 'UP', components: { r2dbc: { status: 'UP' }, diskSpace: { status: 'UP' } } }
		})
	);
});

/** Whether a colour is light: a dark theme has none behind readable content. */
const isLight = (rgb) => {
	const [r, g, b] = rgb.match(/\d+/g).map(Number);
	return 0.2126 * r + 0.7152 * g + 0.0722 * b > 140;
};

for (const [width, height] of [
	[1280, 800],
	[360, 740]
]) {
	for (const path of PAGES) {
		test(`${path} is dark at ${width} px`, async ({ page }) => {
			await page.setViewportSize({ width, height });
			await page.goto(path);
			await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
			await expect(page.getByRole('banner')).toBeVisible();

			const lightPanels = await page.evaluate(() =>
				[...document.querySelectorAll('body *')]
					.filter((el) => {
						const box = el.getBoundingClientRect();
						return box.width > 80 && box.height > 24 && el.checkVisibility();
					})
					.map((el) => ({
						el: el.tagName + '.' + el.className,
						bg: getComputedStyle(el).backgroundColor
					}))
					.filter(({ bg }) => bg !== 'rgba(0, 0, 0, 0)')
					.map(({ el, bg }) => `${el} ${bg}`)
			);
			const offenders = lightPanels.filter((entry) => isLight(entry.slice(entry.indexOf('rgb'))));
			expect(offenders).toEqual([]);
		});
	}
}
