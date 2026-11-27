import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/tune, "Bulk import of thesession.org Tunes".
const ADMIN = { id: 'account-admin', displayName: 'Admin', admin: true };
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };

async function open(page, account) {
	await page.route('**/api/me', (route) => route.fulfill({ json: account }));
	await page.route('**/api/dance-types', (route) => route.fulfill({ json: ['reel'] }));
	await page.route('**/api/tunes?*', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/tunes', (route) => route.fulfill({ json: [] }));
}

test('an administrator starts the import and sees it finish', async ({ page }) => {
	await open(page, ADMIN);
	let state = 'idle';
	await page.route('**/api/tunes/import-dump', (route) => {
		if (route.request().method() === 'POST') state = 'running';
		else if (state === 'running') state = 'done';
		route.fulfill({
			status: route.request().method() === 'POST' ? 202 : 200,
			json: { state, created: state === 'done' ? 3 : 0, skipped: 1, reason: null }
		});
	});
	await page.goto('/tunes');

	await page.getByRole('button', { name: 'Import tunes from thesession.org' }).click();

	await expect(page.getByTestId('dump-import-status')).toHaveText(
		/Import finished: 3 created, 1 skipped/,
		{
			timeout: 10000
		}
	);
});

test('a member does not see the import', async ({ page }) => {
	await open(page, ALICE);
	await page.goto('/tunes');

	await expect(page.getByRole('heading', { name: 'Tunes' })).toBeVisible();
	await expect(page.getByRole('button', { name: /Import tunes/ })).toHaveCount(0);
});

test('the thesession.org credit is on every page', async ({ page }) => {
	await open(page, ALICE);
	await page.goto('/tunes');

	await expect(page.getByRole('link', { name: 'Data from thesession.org' })).toHaveAttribute(
		'href',
		'https://thesession.org'
	);
});
