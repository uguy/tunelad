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
