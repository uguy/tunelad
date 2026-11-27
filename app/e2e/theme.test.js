import { expect, test } from '@playwright/test';

// --- Requirements: System theme by default, Theme switch, No flash of the wrong theme (appearance spec) ------

test.beforeEach(async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [] }));
});

const theme = (page) => page.locator('html').getAttribute('data-theme');

async function choose(page, name) {
	await page
		.getByRole('banner')
		.getByRole('button', { name: /^Theme/ })
		.click();
	await page.getByRole('menuitemradio', { name }).click();
}

test('follows a dark system by default', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'dark' });
	await page.goto('/');
	expect(await theme(page)).toBe('dark');
});

test('follows a light system by default', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'light' });
	await page.goto('/');
	expect(await theme(page)).toBe('light');
});

test('follows a change of the system while open', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'light' });
	await page.goto('/');
	await page.emulateMedia({ colorScheme: 'dark' });
	await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('switch to dark, and the choice is remembered after a reload', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'light' });
	await page.goto('/');

	await choose(page, 'Dark');
	await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

	await page.reload();
	expect(await theme(page)).toBe('dark');
});

test('back to the system theme clears the choice', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'light' });
	await page.goto('/');
	await choose(page, 'Dark');

	await choose(page, 'System');

	await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
	expect(await page.evaluate(() => localStorage.getItem('tunelad.theme'))).toBeNull();
});

test('the switch tells the choice', async ({ page }) => {
	await page.goto('/');
	await choose(page, 'Light');
	await expect(
		page.getByRole('banner').getByRole('button', { name: 'Theme: Light' })
	).toBeVisible();
});

test('the theme is set before the first paint', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'light' });
	await page.addInitScript(() => localStorage.setItem('tunelad.theme', 'dark'));
	// The theme must already be on <html> when the document finishes parsing, before any script of the app runs
	await page.addInitScript(() => {
		document.addEventListener('DOMContentLoaded', () => {
			window.__themeAtParse = document.documentElement.dataset.theme;
		});
	});
	await page.goto('/');
	expect(await page.evaluate(() => window.__themeAtParse)).toBe('dark');
	const background = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
	expect(background).toBe('rgb(18, 18, 21)');
});

test('the mobile menu has the theme switch', async ({ page }) => {
	await page.setViewportSize({ width: 360, height: 700 });
	await page.goto('/');
	await page.getByRole('button', { name: 'Open main menu' }).click();
	await page.getByRole('group', { name: 'Theme' }).getByRole('button', { name: 'Dark' }).click();
	await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});
