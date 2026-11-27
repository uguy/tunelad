import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/tune (UI requirements).
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const COOLEYS = {
	id: 'tune-1',
	name: "Cooley's",
	aliases: ["Cooley's Reel"],
	author: null,
	danceType: 'reel',
	key: 'Edorian',
	sourceId: 27,
	abc: "X:1\nT:Cooley's\nM:4/4\nL:1/8\nK:Edor\nEBBA B2 EB|B2 AB dBAG|"
};
const KESH = {
	id: 'tune-2',
	name: 'The Kesh',
	aliases: ['Kesh Jig'],
	author: null,
	danceType: 'jig',
	key: null,
	sourceId: null,
	abc: null
};

async function mockApi(page, { loggedIn = true } = {}) {
	const state = { imported: [] };
	await page.route('**/api/me', (route) =>
		loggedIn ? route.fulfill({ json: ALICE }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/dance-types', (route) =>
		route.fulfill({ json: ['jig', 'reel', 'hornpipe'] })
	);
	await page.route('**/api/tunes?*', (route) => {
		const url = new URL(route.request().url());
		const type = url.searchParams.get('danceType');
		return route.fulfill({
			json: [COOLEYS, KESH].filter((t) => !type || t.danceType === type)
		});
	});
	await page.route('**/api/tunes/import', (route) => {
		state.imported.push(route.request().postDataJSON().id);
		return route.fulfill({ status: 201, json: COOLEYS });
	});
	await page.route('**/api/tunes/tune-*', (route) => {
		const id = new URL(route.request().url()).pathname.split('/')[3];
		const tune = [COOLEYS, KESH].find((t) => t.id === id);
		return route.fulfill(tune ? { json: tune } : { status: 404 });
	});
	return state;
}

// --- Requirement: Tunes pages ----------------------------------------------------------------

test('tunes are listed and filtered by dance type', async ({ page }) => {
	await mockApi(page);
	await page.goto('/tunes');
	await expect(page.getByTestId('tune-name')).toHaveText(["Cooley's", 'The Kesh']);

	await page.getByLabel('Dance type').selectOption('jig');

	await expect(page.getByTestId('tune-name')).toHaveText(['The Kesh']);
});

test('import from the UI', async ({ page }) => {
	const state = await mockApi(page);
	await page.goto('/tunes/new');

	await page.getByLabel('Tune number or address').fill('https://thesession.org/tunes/27');
	await page.getByRole('button', { name: 'Import' }).click();

	await expect(page.getByRole('heading', { name: "Cooley's" })).toBeVisible();
	expect(state.imported).toEqual([27]);
});

// --- Requirement: View a Reference transcription ---------------------------------------------

test('switch to the ABC text', async ({ page }) => {
	await mockApi(page);
	await page.goto('/tunes/tune-1');

	await expect(
		page.getByRole('img', { name: /Sheet music of Cooley's/ }).locator('svg')
	).toBeVisible();
	await page.getByRole('button', { name: 'ABC text' }).click();

	await expect(page.getByText('K:Edor')).toBeVisible();
	await expect(page.locator('svg.abcjs-svg')).toHaveCount(0);
});

test('no transcription', async ({ page }) => {
	await mockApi(page);
	await page.goto('/tunes/tune-2');

	await expect(page.getByText('no transcription')).toBeVisible();
	await expect(page.getByRole('button', { name: 'Play' })).toHaveCount(0);
	await expect(page.getByRole('button', { name: 'ABC text' })).toHaveCount(0);
});

// --- Requirement: Play a Reference transcription ---------------------------------------------

test('play and stop', async ({ page }) => {
	await mockApi(page);
	await page.goto('/tunes/tune-1');

	await page.getByRole('button', { name: 'Play' }).click();
	await expect(page.getByRole('button', { name: 'Stop' })).toBeVisible({ timeout: 30000 });

	await page.getByRole('button', { name: 'Stop' }).click();
	await expect(page.getByRole('button', { name: 'Play' })).toBeVisible();
});
