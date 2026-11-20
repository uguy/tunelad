import { expect, test } from '@playwright/test';

// UI-only tests for the track spec's edit and delete requirements: the REST API is mocked with page.route.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const BOB = { id: 'account-bob', displayName: 'Bob Smith', admin: false };
const ADMIN = { id: 'account-admin', displayName: 'Tune Lad Admin', admin: true };

const KNEES_UP = {
	id: 'track-1',
	title: 'Knees Up',
	description: 'A reel and a song',
	artist: 'Breabach',
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: ALICE.id, displayName: ALICE.displayName }
};
const MOSSY_BANKS = {
	id: 'track-2',
	title: 'Mossy Banks',
	description: 'Two reels',
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: BOB.id, displayName: BOB.displayName }
};

/** Mocks who is logged in (null for nobody) and the track list, and gives the browser a CSRF cookie. */
async function openTracks(page, context, account) {
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	await page.route('**/api/me', (route) =>
		account ? route.fulfill({ json: account }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/tracks/search*', (route) =>
		route.fulfill({ json: [KNEES_UP, MOSSY_BANKS] })
	);
	await page.goto('/tracks');
	await expect(page.getByText('Knees Up')).toBeVisible();
}

/** Records every PUT or DELETE on a track and answers it with the given response. */
async function recordChanges(page, respond) {
	const requests = [];
	await page.route('**/api/tracks/track-*', (route) => {
		const method = route.request().method();
		if (method !== 'PUT' && method !== 'DELETE') return route.fallback();
		requests.push(route.request());
		return respond(route);
	});
	return requests;
}

const editButton = (page, title) => page.getByRole('button', { name: `edit track ${title}` });
const deleteButton = (page, title) => page.getByRole('button', { name: `delete track ${title}` });
const trackTitles = (page) => page.locator('summary span.px-2');

// --- Requirement: Edit and delete own tracks from the UI -------------------------------------

test('controls on own tracks only', async ({ page, context }) => {
	await openTracks(page, context, ALICE);

	await expect(editButton(page, 'Knees Up')).toHaveText('Edit');
	await expect(deleteButton(page, 'Knees Up')).toHaveText('Delete');
	await expect(editButton(page, 'Mossy Banks')).toHaveCount(0);
	await expect(deleteButton(page, 'Mossy Banks')).toHaveCount(0);
});

test('administrator sees controls on every track', async ({ page, context }) => {
	await openTracks(page, context, ADMIN);

	for (const title of ['Knees Up', 'Mossy Banks']) {
		await expect(editButton(page, title)).toBeVisible();
		await expect(deleteButton(page, title)).toBeVisible();
	}
});

test('no controls for anonymous visitors', async ({ page, context }) => {
	await openTracks(page, context, null);

	await expect(page.getByRole('button', { name: /^edit track/ })).toHaveCount(0);
	await expect(page.getByRole('button', { name: /^delete track/ })).toHaveCount(0);
});

// --- Requirement: Edit a track from the UI ---------------------------------------------------

test('save an edit', async ({ page, context }) => {
	await openTracks(page, context, ALICE);
	const requests = await recordChanges(page, (route) =>
		route.fulfill({ json: { ...KNEES_UP, ...route.request().postDataJSON() } })
	);

	await editButton(page, 'Knees Up').click();
	await page.getByLabel('Title').fill('Knees Up (live)');
	await page.getByRole('button', { name: 'Save' }).click();

	await expect(trackTitles(page)).toHaveText(['Knees Up (live)', 'Mossy Banks']);
	expect(requests).toHaveLength(1);
	expect(requests[0].method()).toBe('PUT');
	expect(new URL(requests[0].url()).pathname).toBe('/api/tracks/track-1');
	expect(requests[0].headers()['x-xsrf-token']).toBe('csrf-123');
	expect(requests[0].postDataJSON()).toEqual({
		title: 'Knees Up (live)',
		description: 'A reel and a song',
		artist: 'Breabach',
		album: null
	});
});

test('cancel an edit', async ({ page, context }) => {
	await openTracks(page, context, ALICE);
	const requests = await recordChanges(page, (route) => route.fulfill({ json: KNEES_UP }));

	await editButton(page, 'Knees Up').click();
	await page.getByLabel('Title').fill('Something else');
	await page.getByRole('button', { name: 'Cancel' }).click();

	await expect(page.getByRole('form', { name: 'edit track Knees Up' })).toHaveCount(0);
	await expect(trackTitles(page)).toHaveText(['Knees Up', 'Mossy Banks']);
	expect(requests).toHaveLength(0);
});

test('edit rejected', async ({ page, context }) => {
	await openTracks(page, context, ALICE);
	await recordChanges(page, (route) => route.fulfill({ status: 403 }));

	await editButton(page, 'Knees Up').click();
	await page.getByLabel('Title').fill('Knees Up (live)');
	await page.getByRole('button', { name: 'Save' }).click();

	await expect(page.getByRole('alert')).toHaveText('You may not change this track.');
	await expect(trackTitles(page)).toHaveText(['Knees Up', 'Mossy Banks']);
});

// --- Requirement: Delete a track from the UI -------------------------------------------------

test('confirm a deletion', async ({ page, context }) => {
	await openTracks(page, context, ALICE);
	const requests = await recordChanges(page, (route) => route.fulfill({ status: 204 }));
	page.once('dialog', (dialog) => dialog.accept());

	await deleteButton(page, 'Knees Up').click();

	await expect(trackTitles(page)).toHaveText(['Mossy Banks']);
	expect(requests).toHaveLength(1);
	expect(requests[0].method()).toBe('DELETE');
	expect(new URL(requests[0].url()).pathname).toBe('/api/tracks/track-1');
	expect(requests[0].headers()['x-xsrf-token']).toBe('csrf-123');
});

test('decline a deletion', async ({ page, context }) => {
	await openTracks(page, context, ALICE);
	const requests = await recordChanges(page, (route) => route.fulfill({ status: 204 }));
	let asked = false;
	page.once('dialog', (dialog) => {
		asked = true;
		return dialog.dismiss();
	});

	await deleteButton(page, 'Knees Up').click();

	await expect.poll(() => asked).toBe(true);
	await expect(trackTitles(page)).toHaveText(['Knees Up', 'Mossy Banks']);
	expect(requests).toHaveLength(0);
});

test('deletion rejected', async ({ page, context }) => {
	await openTracks(page, context, ALICE);
	await recordChanges(page, (route) => route.fulfill({ status: 403 }));
	page.once('dialog', (dialog) => dialog.accept());

	await deleteButton(page, 'Knees Up').click();

	await expect(page.getByRole('alert')).toHaveText('You may not change this track.');
	await expect(trackTitles(page)).toHaveText(['Knees Up', 'Mossy Banks']);
});
