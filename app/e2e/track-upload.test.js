import { expect, test } from '@playwright/test';
import { closeSync, openSync, ftruncateSync } from 'node:fs';

// UI-only tests for the track spec's upload requirements: the REST API is mocked with page.route.
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };

const MOSSY_BANKS = {
	id: 'track-2',
	title: 'Mossy Banks',
	description: 'Two reels',
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: 'account-bob', displayName: 'Bob Smith' }
};
const KNEES_UP = {
	id: 'track-new',
	title: 'Knees Up',
	description: 'A reel and a song',
	artist: 'Breabach',
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: ALICE.id, displayName: ALICE.displayName }
};
const COOLEYS = { id: 'tune-1', name: "Cooley's", aliases: [], danceType: 'reel' };
const KESH = { id: 'tune-2', name: 'The Kesh', aliases: [], danceType: 'jig' };
const REEL_AND_JIG = { id: 'set-1', tunes: [COOLEYS, KESH] };
const KNEES_UP_FILE = {
	name: 'knees-up.mp3',
	mimeType: 'audio/mpeg',
	buffer: Buffer.alloc(64 * 1024)
};

/** Mocks who is logged in (null for nobody) and a search that doesn't find new uploads yet, with a CSRF cookie. */
async function mockApi(page, context, account) {
	// Waveforms are not under test here: an empty one, as for a track whose waveform failed
	await page.route('**/api/tracks/*/waveform', (route) =>
		route.fulfill({ json: { status: 'FAILED', peaks: [] } })
	);
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	await page.route('**/api/me', (route) =>
		account ? route.fulfill({ json: account }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/tracks/search*', (route) => route.fulfill({ json: [MOSSY_BANKS] }));
	// The sets to choose from, and the tunes and set creation of the set builder
	await page.route('**/api/sets?*', (route) => route.fulfill({ json: [REEL_AND_JIG] }));
	await page.route('**/api/tunes?*', (route) => route.fulfill({ json: [COOLEYS, KESH] }));
	await page.route('**/api/sets', (route) => {
		const ids = route.request().postDataJSON().tuneIds;
		const tunes = ids.map((id) => [COOLEYS, KESH].find((tune) => tune.id === id));
		return route.fulfill({ status: 201, json: { id: 'set-new', tunes } });
	});
}

const setField = (page) => page.getByRole('combobox', { name: 'Set' });

/** The parts of a recorded multipart upload, by name. */
function partsOf(request) {
	const body = request.postDataBuffer().toString('latin1');
	const parts = {};
	for (const match of body.matchAll(
		/name="(\w+)"(?:; filename="[^"]*")?\r\n(?:[^\r]+\r\n)*\r\n([^\r]*)/g
	))
		parts[match[1]] = match[2];
	return parts;
}

/** Records every upload and answers it with `respond`, which may be called once per upload. */
async function recordUploads(page, respond) {
	const requests = [];
	await page.route('**/api/tracks', (route) => {
		if (route.request().method() !== 'POST') return route.fallback();
		requests.push(route.request());
		return respond(route, requests.length);
	});
	return requests;
}

/** Opens the upload page as alice and fills in the form. */
async function fillUploadForm(page, context, file = KNEES_UP_FILE) {
	await mockApi(page, context, ALICE);
	await page.goto('/tracks/upload');
	await page.getByLabel('Title').fill('Knees Up');
	await page.getByLabel('Description').fill('A reel and a song');
	await page.getByLabel('Artist').fill('Breabach');
	await setField(page).selectOption({ label: "Cooley's, The Kesh" });
	await page.getByLabel(/Audio file/).setInputFiles(file);
}

/** Checks the form still holds what fillUploadForm entered. */
async function expectInputKept(page) {
	await expect(page.getByLabel('Title')).toHaveValue('Knees Up');
	await expect(page.getByLabel('Description')).toHaveValue('A reel and a song');
	await expect(page.getByLabel('Artist')).toHaveValue('Breabach');
	expect(await page.getByLabel(/Audio file/).evaluate((input) => input.files[0]?.name)).toBe(
		'knees-up.mp3'
	);
}

/** A promise, with the function that settles it: lets a test hold a mocked response back. */
function deferred() {
	let release;
	const promise = new Promise((resolve) => (release = resolve));
	return { promise, release };
}

const trackTitles = (page) => page.getByTestId('track-title');
const uploadButton = (page) => page.getByRole('button', { name: 'Upload' });

// --- Requirement: Upload link on the tracks page ---------------------------------------------

test('logged-in user', async ({ page, context }) => {
	await mockApi(page, context, ALICE);
	await page.goto('/tracks');

	await expect(page.getByRole('link', { name: 'Upload' })).toHaveAttribute(
		'href',
		'/tracks/upload'
	);
});

test('anonymous visitor', async ({ page, context }) => {
	await mockApi(page, context, null);
	await page.goto('/tracks');

	await expect(page.getByRole('link', { name: 'Log in to upload' })).toHaveAttribute(
		'href',
		'/login'
	);
	await expect(page.getByRole('link', { name: 'Upload', exact: true })).toHaveCount(0);
});

test('anonymous visitor opens the upload page', async ({ page, context }) => {
	await mockApi(page, context, null);
	await page.goto('/tracks/upload');

	await expect(page.getByRole('link', { name: 'Log in to upload' })).toBeVisible();
	await expect(page.getByRole('form', { name: 'upload a track' })).toHaveCount(0);
});

// --- Requirement: Upload a track from the UI -------------------------------------------------

test('successful upload', async ({ page, context }) => {
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP, headers: { Location: '/api/tracks/track-new' } })
	);
	await fillUploadForm(page, context);

	await uploadButton(page).click();

	await expect(page).toHaveURL('/tracks');
	// Search still returns only Mossy Banks: the new track comes from the upload's response
	await expect(trackTitles(page)).toHaveText(['Knees Up', 'Mossy Banks']);
	expect(requests).toHaveLength(1);
	expect(requests[0].headers()['x-xsrf-token']).toBe('csrf-123');
	expect(requests[0].headers()['content-type']).toMatch(/^multipart\/form-data; boundary=/);
	const body = requests[0].postDataBuffer().toString('latin1');
	expect(body).toMatch(/name="title"\r\n\r\nKnees Up\r\n/);
	expect(body).toMatch(/name="description"\r\n\r\nA reel and a song\r\n/);
	expect(body).toMatch(/name="artist"\r\n\r\nBreabach\r\n/);
	expect(body).not.toContain('name="album"');
	expect(body).toContain('name="file"; filename="knees-up.mp3"');
	expect(body).toMatch(/name="setId"\r\n\r\nset-1\r\n/);
});

test('successful upload with an existing set and performances', async ({ page, context }) => {
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context);
	const second = page.getByRole('group', { name: /The Kesh/ });
	await page
		.getByRole('group', { name: /Cooley's/ })
		.getByLabel('Key')
		.fill('Em');
	await second.getByLabel(/Starts at/).fill('1:35.5');
	await second.getByLabel('Key').fill('G');
	await second.getByLabel('Notes').fill('plays the B part twice');

	await uploadButton(page).click();

	await expect(page).toHaveURL('/tracks');
	const parts = partsOf(requests[0]);
	expect(parts.setId).toBe('set-1');
	expect(JSON.parse(parts.performances)).toEqual([
		{ key: 'Em' },
		{ startOffsetSeconds: 95.5, key: 'G', notes: 'plays the B part twice' }
	]);
});

test('upload with a new set', async ({ page, context }) => {
	const created = [];
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context);
	page.on('request', (request) => {
		if (request.url().endsWith('/api/sets') && request.method() === 'POST')
			created.push(request.postDataJSON());
	});
	await setField(page).selectOption({ label: 'Build a new set…' });
	await page.getByRole('button', { name: 'Add The Kesh' }).click();
	await page.getByRole('button', { name: "Add Cooley's" }).click();
	await page.getByRole('button', { name: "Move Cooley's up" }).click();

	await uploadButton(page).click();

	await expect(page).toHaveURL('/tracks');
	expect(created).toEqual([{ tuneIds: ['tune-1', 'tune-2'] }]);
	expect(partsOf(requests[0]).setId).toBe('set-new');
});

test('performance fields follow the set', async ({ page, context }) => {
	await mockApi(page, context, ALICE);
	await page.goto('/tracks/upload');
	await expect(page.getByRole('group')).toHaveCount(0);

	await setField(page).selectOption({ label: "Cooley's, The Kesh" });

	await expect(page.getByRole('group')).toHaveCount(2);
	await expect(page.getByRole('group').first()).toContainText("Cooley's");
	await expect(page.getByRole('group').nth(1)).toContainText('The Kesh');
});

test('set left empty', async ({ page, context }) => {
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context);
	await setField(page).selectOption('');

	await uploadButton(page).click();

	expect(await setField(page).evaluate((select) => select.validity.valueMissing)).toBe(true);
	expect(requests).toHaveLength(0);
});

test('required field left empty', async ({ page, context }) => {
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context);
	await page.getByLabel('Title').fill('');

	await uploadButton(page).click();

	expect(await page.getByLabel('Title').evaluate((input) => input.validity.valueMissing)).toBe(
		true
	);
	await expect(page).toHaveURL('/tracks/upload');
	expect(requests).toHaveLength(0);
});

// --- Requirement: Upload progress and cancel in the UI ---------------------------------------

test('progress shown', async ({ page, context }) => {
	const response = deferred();
	await recordUploads(page, async (route) => {
		await response.promise;
		await route.fulfill({ status: 201, json: KNEES_UP });
	});
	await fillUploadForm(page, context);

	await uploadButton(page).click();

	await expect(page.getByRole('progressbar', { name: 'upload progress' })).toBeVisible();
	await expect(uploadButton(page)).toBeDisabled();
	response.release();
	await expect(page).toHaveURL('/tracks');
});

test('cancel an upload', async ({ page, context }) => {
	const response = deferred();
	await recordUploads(page, async (route) => {
		await response.promise;
		// The browser has aborted the request by then
		await route.fulfill({ status: 201, json: KNEES_UP }).catch(() => {});
	});
	await fillUploadForm(page, context);

	await uploadButton(page).click();
	await page.getByRole('button', { name: 'Cancel' }).click();

	await expect(page.getByRole('alert')).toContainText('The upload was cancelled');
	await expect(page.getByRole('progressbar')).toHaveCount(0);
	await expect(uploadButton(page)).toBeEnabled();
	await expectInputKept(page);
	response.release();
	await expect(page).toHaveURL('/tracks/upload');
});

// --- Requirement: Failed upload handling in the UI -------------------------------------------

const refusals = [
	[413, 'This file is larger than the 100 MB limit.'],
	[415, "This file type isn't supported: choose an mp3, m4a, ogg, flac or wav file."],
	[400, 'A track needs a title, a description, a set and an audio file, and valid performances.'],
	[401, 'Your session has expired. Log in again, then upload the track again.'],
	[403, 'You may not upload this track. Reload the page and try again.'],
	[500, 'The server could not store the track (500). Try again.']
];

for (const [status, message] of refusals) {
	test(`server refuses the upload with ${status}`, async ({ page, context }) => {
		await recordUploads(page, (route) => route.fulfill({ status }));
		await fillUploadForm(page, context);

		await uploadButton(page).click();

		await expect(page.getByRole('alert')).toContainText(message);
		await expectInputKept(page);
		if (status === 401) {
			await expect(page.getByRole('alert').getByRole('link', { name: 'Log in' })).toHaveAttribute(
				'href',
				'/login'
			);
		}
	});
}

test('server refuses the upload: connection failure', async ({ page, context }) => {
	await recordUploads(page, (route) => route.abort('connectionreset'));
	await fillUploadForm(page, context);

	await uploadButton(page).click();

	await expect(page.getByRole('alert')).toContainText(
		'The connection was lost. Your file is still selected: try again.'
	);
	await expectInputKept(page);
});

test('retry after a failure', async ({ page, context }) => {
	const requests = await recordUploads(page, (route, attempt) =>
		attempt === 1 ? route.abort('connectionreset') : route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context);
	await uploadButton(page).click();
	await expect(page.getByRole('alert')).toContainText('The connection was lost');

	await uploadButton(page).click();

	await expect(page).toHaveURL('/tracks');
	await expect(trackTitles(page)).toHaveText(['Knees Up', 'Mossy Banks']);
	expect(requests).toHaveLength(2);
});

test('file refused before sending: unsupported format', async ({ page, context }) => {
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context, {
		name: 'notes.txt',
		mimeType: 'text/plain',
		buffer: Buffer.from('hi')
	});

	await uploadButton(page).click();

	await expect(page.getByRole('alert')).toContainText("This file type isn't supported");
	expect(requests).toHaveLength(0);
});

test('file refused before sending: over 100 MB', async ({ page, context }, testInfo) => {
	// A sparse file one byte over the limit: no memory or real disk space needed
	const path = testInfo.outputPath('too-big.wav');
	const fd = openSync(path, 'w');
	ftruncateSync(fd, 100 * 1024 * 1024 + 1);
	closeSync(fd);
	const requests = await recordUploads(page, (route) =>
		route.fulfill({ status: 201, json: KNEES_UP })
	);
	await fillUploadForm(page, context, path);

	await uploadButton(page).click();

	await expect(page.getByRole('alert')).toContainText('This file is larger than the 100 MB limit.');
	expect(requests).toHaveLength(0);
});
