import { expect, test } from '@playwright/test';

test('index page has expected h1', async ({ page }) => {
	// The REST API is mocked: nobody is logged in
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.goto('/');
	await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome to Tune Lad !');
});
