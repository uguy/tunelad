import { expect, test } from '@playwright/test';

// Service workers only run against the production build that `vite preview` serves
test.use({ serviceWorkers: 'allow' });

test('service worker registers and controls the page after a reload', async ({ page }) => {
	const errors = [];
	page.on('console', (msg) => {
		// ReloadPrompt logs registration errors with console.log
		if (msg.text().includes('SW registration error')) errors.push(msg.text());
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

test('offline-ready toast is a polite status, not an alert', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.goto('/');
	await expect(page.getByRole('status')).toContainText('App ready to work offline');
	await expect(page.getByRole('alert')).toHaveCount(0);
});

test('service worker leaves backend navigations to the server', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.goto('/');
	await page.evaluate(async () => {
		await navigator.serviceWorker.ready;
	});
	await page.reload();
	await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);

	// The provider's callback lands under /login: mocked, so that what the backend would answer (a redirect back
	// to the login page, which the worker does serve) cannot hide whether the worker let the request through
	await page.route('**/login/oauth2/code/github', (route) =>
		route.fulfill({ contentType: 'text/html', body: '<p>login callback</p>' })
	);

	// These must hit the network (here the preview proxy) rather than the precached SPA shell
	for (const path of [
		'/login/oauth2/code/github',
		'/oauth2/authorization/github',
		'/logout',
		'/api/tracks'
	]) {
		const response = await page.goto(path);
		expect(response?.fromServiceWorker(), path).toBe(false);
	}

	// Client-side routes, the login page among them, are still served from the precached shell
	for (const path of ['/about', '/login']) {
		const response = await page.goto(path);
		expect(response?.fromServiceWorker(), path).toBe(true);
	}
});
