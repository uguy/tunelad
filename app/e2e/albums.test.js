import { expect, test } from '@playwright/test';

// UI-only tests for the track spec's Albums requirements: the REST API is mocked with page.route.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };

const COOLEYS = { id: 'tune-1', name: "Cooley's", aliases: [], danceType: 'reel', abc: null };

const CATALOGUE_TRACK = {
	id: 'cat-1',
	title: "Cooley's",
	artist: 'Dervish',
	album: null,
	format: null,
	catalogue: true,
	albumId: 'album-1',
	albumPosition: 1,
	performances: [
		{
			position: 1,
			tuneId: 'tune-1',
			tuneName: "Cooley's",
			danceType: 'reel',
			startOffsetSeconds: 0
		},
		{
			position: 2,
			tuneId: null,
			tuneName: null,
			listedName: 'The Silver Spear',
			startOffsetSeconds: 0
		}
	]
};
const PLAYABLE_TRACK = {
	id: 'track-2',
	title: 'Mossy Banks',
	artist: 'Dervish',
	album: null,
	format: 'MP3',
	catalogue: false,
	albumId: 'album-1',
	albumPosition: 2,
	performances: []
};
const ALBUM = {
	id: 'album-1',
	title: 'At the End of the Day',
	artist: 'Dervish',
	artistId: 'artist-1',
	releaseDate: '1996-05-01',
	tracks: [CATALOGUE_TRACK, PLAYABLE_TRACK]
};
const ALBUM_ITEM = { ...ALBUM, tracks: [] };
const UPLOADED = {
	id: 'track-new',
	title: "cooley's",
	artist: 'Dervish',
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: ALICE.id, displayName: ALICE.displayName }
};
const FILE = { name: 'cooleys.mp3', mimeType: 'audio/mpeg', buffer: Buffer.alloc(64 * 1024) };

async function mockApi(page, context, { account = ALICE, suggestions = [] } = {}) {
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	const state = { created: null, uploads: [], attached: [], deleted: [] };
	await page.route('**/api/me', (route) =>
		account ? route.fulfill({ json: account }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/sets?*', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/albums/search*', (route) => route.fulfill({ json: [ALBUM_ITEM] }));
	await page.route('**/api/albums/album-1', (route) => route.fulfill({ json: ALBUM }));
	await page.route('**/api/albums', (route) => {
		if (route.request().method() !== 'POST') return route.fallback();
		state.created = route.request().postDataJSON();
		return route.fulfill({ status: 201, json: ALBUM });
	});
	await page.route('**/api/tunes/tune-1', (route) => route.fulfill({ json: COOLEYS }));
	await page.route('**/api/tunes/*/performances', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/tunes/*/albums', (route) => route.fulfill({ json: [ALBUM_ITEM] }));
	await page.route('**/api/tracks/track-new/catalogue-suggestions', (route) =>
		route.fulfill({ json: suggestions })
	);
	await page.route('**/api/tracks/cat-1/audio', (route) => {
		state.attached.push(route.request().method());
		return route.fulfill({ json: { ...CATALOGUE_TRACK, catalogue: false, format: 'MP3' } });
	});
	await page.route('**/api/tracks/track-new', (route) => {
		if (route.request().method() !== 'DELETE') return route.fallback();
		state.deleted.push('track-new');
		return route.fulfill({ status: 204 });
	});
	await page.route('**/api/tracks', (route) => {
		if (route.request().method() !== 'POST') return route.fallback();
		state.uploads.push(route.request().postDataBuffer().toString('latin1'));
		return route.fulfill({ status: 201, json: UPLOADED });
	});
	return state;
}

// --- Requirement: Albums pages ----------------------------------------------------------------

test('albums are listed, linked from the navigation and open by keyboard', async ({
	page,
	context
}) => {
	await mockApi(page, context);
	await page.goto('/');

	await page.getByRole('link', { name: 'Albums', exact: true }).first().click();
	await expect(page.getByTestId('album-title')).toHaveText(['At the End of the Day']);
	await expect(page.getByRole('link', { name: /At the End of the Day/ })).toContainText('1996');

	await page.getByRole('link', { name: /At the End of the Day/ }).focus();
	await page.keyboard.press('Enter');
	await expect(page).toHaveURL('/albums/album-1');
});

test('an album lists its tracks in order, those without audio marked', async ({
	page,
	context
}) => {
	await mockApi(page, context);
	await page.goto('/albums/album-1');

	await expect(page.getByRole('heading', { level: 1 })).toHaveText('At the End of the Day');
	const tracks = page.getByTestId('album-track');
	await expect(tracks).toHaveCount(2);
	await expect(tracks.nth(0)).toContainText("Cooley's");
	await expect(tracks.nth(0)).toContainText('No audio yet');
	await expect(tracks.nth(0).getByRole('link', { name: /Play/ })).toHaveCount(0);
	await expect(tracks.nth(1)).not.toContainText('No audio yet');
	await expect(tracks.nth(1).getByRole('link', { name: /Play/ })).toHaveAttribute(
		'href',
		'/tracks?play=track-2'
	);
});

test('a resolved entry links to its tune, an unresolved one shows the listed name', async ({
	page,
	context
}) => {
	await mockApi(page, context);
	await page.goto('/albums/album-1');

	const first = page.getByTestId('album-track').first();
	await expect(first.getByRole('link', { name: "Cooley's" })).toHaveAttribute(
		'href',
		'/tunes/tune-1'
	);
	await expect(first.getByText('The Silver Spear')).toBeVisible();
	await expect(first.getByRole('link', { name: 'The Silver Spear' })).toHaveCount(0);
});

test('the tune view lists its albums', async ({ page, context }) => {
	await mockApi(page, context);
	await page.goto('/tunes/tune-1');

	await expect(page.getByRole('heading', { name: 'On albums' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'At the End of the Day' })).toHaveAttribute(
		'href',
		'/albums/album-1'
	);
});

// --- Requirement: An Album groups Tracks by one Artist ---------------------------------------

test('create an album by hand', async ({ page, context }) => {
	const state = await mockApi(page, context);
	await page.goto('/albums/new');

	await page.getByLabel('Title').fill('At the End of the Day');
	await page.getByLabel('Artist').fill('Dervish');
	await page.getByLabel(/Release date/).fill('1996-05-01');
	await page.getByRole('button', { name: 'Create' }).click();

	await expect(page).toHaveURL('/albums/album-1');
	expect(state.created).toEqual({
		title: 'At the End of the Day',
		artist: 'Dervish',
		releaseDate: '1996-05-01'
	});
});

test('anonymous visitors cannot add albums', async ({ page, context }) => {
	await mockApi(page, context, { account: null });
	await page.goto('/albums');
	await expect(page.getByRole('link', { name: 'Add an album' })).toHaveCount(0);

	await page.goto('/albums/new');
	await expect(page.getByRole('main').getByRole('link', { name: 'Log in' })).toBeVisible();
	await expect(page.getByRole('form', { name: 'add an album' })).toHaveCount(0);
});

// --- Requirements: Attach uploaded audio / Suggest a Catalogue Track -------------------------

test('choose a catalogue track to attach the upload to', async ({ page, context }) => {
	const state = await mockApi(page, context);
	await page.goto('/tracks/upload');

	await page.getByLabel('Artist').fill('Dervish');
	await page.getByLabel('Artist').blur();
	await page.getByLabel(/^Album of Dervish/).selectOption({ label: 'At the End of the Day' });
	await page.getByLabel(/Track on the album without audio/).selectOption({ label: "1. Cooley's" });
	await page.getByLabel(/Audio file/).setInputFiles(FILE);
	await page.getByRole('button', { name: 'Upload' }).click();

	await expect(page).toHaveURL('/tracks');
	expect(state.uploads).toHaveLength(1);
	expect(state.uploads[0]).toContain('name="catalogueTrackId"\r\n\r\ncat-1');
});

test('the catalogue choices only show for an artist with albums', async ({ page, context }) => {
	await mockApi(page, context);
	await page.goto('/tracks/upload');
	await expect(page.getByLabel(/^Album of/)).toHaveCount(0);

	await page.getByLabel('Artist').fill('Altan');
	await page.getByLabel('Artist').blur();

	await expect(page.getByLabel(/^Album of/)).toHaveCount(0);
});

test('accept a suggestion: the audio goes to the catalogue track', async ({ page, context }) => {
	const state = await mockApi(page, context, { suggestions: [CATALOGUE_TRACK] });
	await page.goto('/tracks/upload');
	await page.getByLabel('Artist').fill('Dervish');
	await page.getByLabel(/Audio file/).setInputFiles(FILE);
	await page.getByRole('button', { name: 'Upload' }).click();

	const banner = page.getByRole('heading', { name: /recording from the catalogue/ });
	await expect(banner).toBeVisible();
	await expect(page.locator('#suggestion-region')).toContainText("Cooley's");
	await expect(page.locator('#suggestion-region')).toContainText('No audio yet');
	await page.getByRole('button', { name: "accept Cooley's" }).click();

	await expect(page).toHaveURL('/tracks');
	expect(state.attached).toEqual(['POST']);
	expect(state.deleted).toEqual(['track-new']);
});

test('ignore a suggestion: the upload stays a track of its own', async ({ page, context }) => {
	const state = await mockApi(page, context, { suggestions: [CATALOGUE_TRACK] });
	await page.goto('/tracks/upload');
	await page.getByLabel('Artist').fill('Dervish');
	await page.getByLabel(/Audio file/).setInputFiles(FILE);
	await page.getByRole('button', { name: 'Upload' }).click();

	await page.getByRole('button', { name: 'Ignore' }).click();

	await expect(page).toHaveURL('/tracks');
	await expect(page.getByTestId('track-title')).toHaveText(["cooley's"]);
	expect(state.attached).toEqual([]);
	expect(state.deleted).toEqual([]);
});

test('no suggestion: the upload goes straight to the tracks page', async ({ page, context }) => {
	await mockApi(page, context, { suggestions: [] });
	await page.goto('/tracks/upload');
	await page.getByLabel('Artist').fill('Dervish');
	await page.getByLabel(/Audio file/).setInputFiles(FILE);
	await page.getByRole('button', { name: 'Upload' }).click();

	await expect(page).toHaveURL('/tracks');
});

// --- Requirement: A Catalogue Track has no audio yet ------------------------------------------

test('a catalogue track in the tracks list has no player', async ({ page, context }) => {
	await mockApi(page, context);
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [CATALOGUE_TRACK] }));
	await page.goto('/tracks');

	await expect(page.getByTestId('no-audio')).toHaveText('No audio yet');
	await expect(page.getByRole('button', { name: /play track Cooley's/ })).toBeDisabled();
});
