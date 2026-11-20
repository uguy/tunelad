import { expect, test } from '@playwright/test';

// Service workers only run against the production build that `vite preview` serves
test.use({ serviceWorkers: 'allow' });

test('service worker registers and controls the page after a reload', async ({ page }) => {
	const errors = [];
	page.on('console', (msg) => {
		if (msg.type() === 'error' && msg.text().includes('SW registration error'))
			errors.push(msg.text());
	});
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));

	await page.goto('/');
	const scope = await page.evaluate(async () => (await navigator.serviceWorker.ready).scope);
	expect(scope).toMatch(/\/$/);

	await page.reload();
	await expect
		.poll(() => page.evaluate(() => navigator.serviceWorker.controller?.state ?? null))
		.toBe('activated');
	expect(errors).toEqual([]);
});

test('service worker leaves backend navigations to the server', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.goto('/');
	await page.evaluate(async () => {
		await navigator.serviceWorker.ready;
	});
	await page.reload();
	await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);

	// These must hit the network (here the preview proxy) rather than the precached SPA shell
	for (const path of ['/login', '/oauth2/authorization/github', '/logout', '/api/tracks']) {
		const response = await page.goto(path);
		expect(response?.fromServiceWorker(), path).toBe(false);
	}

	// Client-side routes are still served from the precached shell
	const response = await page.goto('/about');
	expect(response?.fromServiceWorker()).toBe(true);
});
