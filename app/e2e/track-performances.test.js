import { expect, test } from '@playwright/test';

// UI-only tests for the track spec's Set and Performances (track list, edit): the REST API is mocked with page.route.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const OWNER = { id: ALICE.id, displayName: ALICE.displayName };

const performance = (position, tuneId, tuneName, danceType, startOffsetSeconds, key, notes) => ({
	position,
	tuneId,
	tuneName,
	danceType,
	startOffsetSeconds,
	key,
	notes
});
const SET = {
	id: 'set-1',
	tunes: [
		{ id: 'tune-1', name: "Cooley's", danceType: 'reel' },
		{ id: 'tune-2', name: 'The Kesh', danceType: 'jig' }
	]
};
const PERFORMANCES = [
	performance(1, 'tune-1', "Cooley's", 'reel', 0, 'Em', null),
	performance(2, 'tune-2', 'The Kesh', 'jig', 95.5, 'G', 'plays the B part twice')
];
// Search results carry neither the set nor the performances
const LISTED = {
	id: 'track-1',
	title: 'Knees Up',
	description: 'A reel and a jig',
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: OWNER
};
const WHOLE = { ...LISTED, set: SET, performances: PERFORMANCES };

async function openTracks(page, context) {
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	await page.route('**/api/me', (route) => route.fulfill({ json: ALICE }));
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [LISTED] }));
	await page.route('**/api/tracks/track-1', (route) => {
		if (route.request().method() === 'PUT') {
			return route.fulfill({
				json: { ...WHOLE, performances: route.request().postDataJSON().performances }
			});
		}
		return route.fulfill({ json: WHOLE });
	});
	await page.goto('/tracks');
	await expect(page.getByText('Knees Up')).toBeVisible();
}

test('performances shown with the track', async ({ page, context }) => {
	await openTracks(page, context);

	await page.getByRole('button', { name: 'show track Knees Up detail button' }).click();

	const tunes = page.getByRole('list', { name: 'tunes of Knees Up' }).getByRole('listitem');
	await expect(tunes).toHaveCount(2);
	await expect(tunes.nth(0)).toContainText("Cooley's");
	await expect(tunes.nth(0)).toContainText('key Em');
	await expect(tunes.nth(1)).toContainText('starts at 1:35.5');
	await expect(tunes.nth(1)).toContainText('key G');
	await expect(tunes.nth(1)).toContainText('plays the B part twice');
});

test('a track without a set shows no tunes', async ({ page, context }) => {
	await openTracks(page, context);
	await page.route('**/api/tracks/track-1', (route) =>
		route.fulfill({ json: { ...LISTED, set: null, performances: [] } })
	);

	await page.getByRole('button', { name: 'show track Knees Up detail button' }).click();

	await expect(page.getByText('A reel and a jig')).toBeVisible();
	await expect(page.getByRole('list', { name: 'tunes of Knees Up' })).toHaveCount(0);
});

test('edit the performances of a track', async ({ page, context }) => {
	await openTracks(page, context);
	const puts = [];
	page.on('request', (request) => {
		if (request.method() === 'PUT') puts.push(request.postDataJSON());
	});

	await page.getByRole('button', { name: 'edit track Knees Up' }).click();
	const second = page.getByRole('group', { name: /The Kesh/ });
	await expect(second.getByLabel(/Starts at/)).toHaveValue('1:35.5');
	await second.getByLabel(/Starts at/).fill('2:00');
	await second.getByLabel('Key').fill('Am');
	await page.getByRole('button', { name: 'Save' }).click();

	await expect(page.getByRole('form', { name: 'edit track Knees Up' })).toHaveCount(0);
	expect(puts).toHaveLength(1);
	expect(puts[0].performances).toEqual([
		{ key: 'Em' },
		{ startOffsetSeconds: 120, key: 'Am', notes: 'plays the B part twice' }
	]);
});

test('write a performance transcription', async ({ page, context }) => {
	await openTracks(page, context);
	const puts = [];
	page.on('request', (request) => {
		if (request.method() === 'PUT') puts.push(request.postDataJSON());
	});

	await page.getByRole('button', { name: 'edit track Knees Up' }).click();
	await page
		.getByRole('group', { name: /The Kesh/ })
		.getByLabel(/Transcription/)
		.fill('X:1\nK:G\nGABc|');
	await page.getByRole('button', { name: 'Save' }).click();

	await expect(page.getByRole('form', { name: 'edit track Knees Up' })).toHaveCount(0);
	expect(puts[0].performances[1].abc).toBe('X:1\nK:G\nGABc|');
	expect(puts[0].performances[0]).not.toHaveProperty('abc');
});

test('an invalid start is refused before sending', async ({ page, context }) => {
	await openTracks(page, context);
	const puts = [];
	page.on('request', (request) => request.method() === 'PUT' && puts.push(request));

	await page.getByRole('button', { name: 'edit track Knees Up' }).click();
	await page
		.getByRole('group', { name: /Cooley's/ })
		.getByLabel(/Starts at/)
		.fill('soon');
	await page.getByRole('button', { name: 'Save' }).click();

	await expect(page.getByRole('alert')).toContainText('The start must be a time');
	expect(puts).toHaveLength(0);
});

// --- Identification: where each performance stands, and asking again ------------------------------

const IDENTIFIED = {
	...WHOLE,
	performances: [
		{ ...PERFORMANCES[0], identification: 'nothing_found' },
		{ ...PERFORMANCES[1], identification: 'identified' }
	]
};

test('each performance says where it stands in identification', async ({ page, context }) => {
	await openTracks(page, context);
	await page.route('**/api/tracks/track-1', (route) => route.fulfill({ json: IDENTIFIED }));

	await page.getByRole('button', { name: 'show track Knees Up detail button' }).click();

	const tunes = page.getByRole('list', { name: 'tunes of Knees Up' }).getByRole('listitem');
	await expect(tunes.nth(0)).toContainText('Nothing found in the audio');
	await expect(tunes.nth(1)).toContainText('Identified from the audio');
});

test('the edit form shows the state next to each tune', async ({ page, context }) => {
	await openTracks(page, context);
	await page.route('**/api/tracks/track-1', (route) => route.fulfill({ json: IDENTIFIED }));

	await page.getByRole('button', { name: 'edit track Knees Up' }).click();

	await expect(page.getByRole('group', { name: /The Kesh/ })).toContainText(
		'Identified from the audio'
	);
});

test('the owner asks for identification again', async ({ page, context }) => {
	await openTracks(page, context);
	await page.route('**/api/tracks/track-1', (route) => route.fulfill({ json: IDENTIFIED }));
	const posts = [];
	await page.route('**/api/tracks/track-1/identification', (route) => {
		posts.push(route.request().headers()['x-xsrf-token']);
		return route.fulfill({ status: 202 });
	});

	await page.getByRole('button', { name: 'show track Knees Up detail button' }).click();
	await page.getByRole('button', { name: 'identify the tunes of Knees Up again' }).click();

	await expect(page.getByRole('status')).toContainText('Listening to the track');
	expect(posts).toEqual(['csrf-123']);
});

test('a refused request is reported', async ({ page, context }) => {
	await openTracks(page, context);
	await page.route('**/api/tracks/track-1', (route) => route.fulfill({ json: IDENTIFIED }));
	await page.route('**/api/tracks/track-1/identification', (route) =>
		route.fulfill({ status: 403 })
	);

	await page.getByRole('button', { name: 'show track Knees Up detail button' }).click();
	await page.getByRole('button', { name: 'identify the tunes of Knees Up again' }).click();

	await expect(page.getByRole('alert')).toContainText('You may not change this track.');
});

test('someone else cannot ask again', async ({ page, context }) => {
	await openTracks(page, context);
	await page.route('**/api/tracks/search*', (route) =>
		route.fulfill({ json: [{ ...LISTED, owner: { id: 'account-bob', displayName: 'Bob Smith' } }] })
	);
	await page.route('**/api/tracks/track-1', (route) =>
		route.fulfill({
			json: { ...IDENTIFIED, owner: { id: 'account-bob', displayName: 'Bob Smith' } }
		})
	);
	await page.reload();

	await page.getByRole('button', { name: 'show track Knees Up detail button' }).click();

	await expect(page.getByRole('list', { name: 'tunes of Knees Up' })).toBeVisible();
	await expect(
		page.getByRole('button', { name: 'identify the tunes of Knees Up again' })
	).toHaveCount(0);
});
