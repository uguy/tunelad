import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/set (UI requirements).
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const COOLEYS = { id: 'tune-1', name: "Cooley's", aliases: [], danceType: 'reel' };
const KESH = { id: 'tune-2', name: 'The Kesh', aliases: [], danceType: 'jig' };
const set = (id, tunes) => ({ id, tunes });

async function mockApi(page, { existing = [] } = {}) {
	const state = { created: [], sets: [...existing] };
	await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
	await page.route('**/api/tunes?*', (route) => route.fulfill({ json: [COOLEYS, KESH] }));
	await page.route('**/api/sets?*', (route) => route.fulfill({ json: state.sets }));
	await page.route('**/api/sets', (route) => {
		const ids = route.request().postDataJSON().tuneIds;
		state.created.push(ids);
		const tunes = ids.map((id) => [COOLEYS, KESH].find((t) => t.id === id));
		const known = state.sets.find((s) => s.tunes.map((t) => t.id).join() === ids.join());
		const result = known ?? set('set-new', tunes);
		if (!known) state.sets.push(result);
		return route.fulfill({ status: known ? 200 : 201, json: result });
	});
	await page.route('**/api/sets/set-*', (route) => {
		const id = new URL(route.request().url()).pathname.split('/')[3];
		const found = state.sets.find((s) => s.id === id);
		return route.fulfill(found ? { json: found } : { status: 404 });
	});
	return state;
}

// --- Requirement: Sets in the UI ------------------------------------------------------------

test('sets are listed with their tunes in order', async ({ page }) => {
	await mockApi(page, { existing: [set('set-1', [COOLEYS, KESH])] });
	await page.goto('/sets');

	await expect(page.getByTestId('set-tunes').first()).toContainText("Cooley's");
	await page.getByRole('link', { name: /Cooley's/ }).click();
	await expect(
		page.getByRole('list', { name: 'tunes of the set' }).getByRole('listitem')
	).toHaveCount(2);
});

test('build a set, reorder it and create it', async ({ page }) => {
	const state = await mockApi(page);
	await page.goto('/sets/new');

	await page.getByRole('button', { name: 'Add The Kesh' }).click();
	await page.getByRole('button', { name: "Add Cooley's" }).click();
	await page.getByRole('button', { name: "Move Cooley's up" }).click();
	await expect(page.getByRole('status')).toHaveText("Cooley's moved to position 1 of 2.");
	await page.getByRole('button', { name: 'Create the set' }).click();

	await expect(page).toHaveURL(/\/sets\/set-new$/);
	await expect(
		page.getByRole('list', { name: 'tunes of the set' }).getByRole('listitem')
	).toHaveText([/Cooley's/, /The Kesh/]);
	expect(state.created).toEqual([['tune-1', 'tune-2']]);
	await expect(page.getByText('already existed')).toHaveCount(0);
});

test('remove a tune, then the Create button is disabled for an empty set', async ({ page }) => {
	await mockApi(page);
	await page.goto('/sets/new');
	await expect(page.getByRole('button', { name: 'Create the set' })).toBeDisabled();

	await page.getByRole('button', { name: "Add Cooley's" }).click();
	await page.getByRole('button', { name: "Remove Cooley's" }).click();

	await expect(page.getByRole('button', { name: 'Create the set' })).toBeDisabled();
});

test('the set already existed', async ({ page }) => {
	await mockApi(page, { existing: [set('set-1', [COOLEYS, KESH])] });
	await page.goto('/sets/new');

	await page.getByRole('button', { name: "Add Cooley's" }).click();
	await page.getByRole('button', { name: 'Add The Kesh' }).click();
	await page.getByRole('button', { name: 'Create the set' }).click();

	await expect(page).toHaveURL(/\/sets\/set-1$/);
	await expect(page.getByText('This set already existed')).toBeVisible();
});
