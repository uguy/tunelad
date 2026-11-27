import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/changes/band-and-musician (tune).
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const KESH = {
	id: 'tune-2',
	name: 'The Kesh',
	aliases: [],
	author: null,
	danceType: 'jig',
	key: null,
	sourceId: null,
	abc: null
};
const performance = (trackId, title, artist) => ({
	trackId,
	trackTitle: title,
	artist: artist?.name ?? null,
	artistId: artist?.id ?? null,
	artistKind: artist?.kind ?? null,
	position: 1,
	startOffsetSeconds: 0,
	key: null,
	notes: null,
	abc: null
});

/** Mocks the Tune, its Performances and the Artists, which edits change like the server would. */
async function mockApi(page, { loggedIn = false, artists, performances }) {
	const state = { artists: structuredClone(artists) };
	await page.route('**/api/me', (route) =>
		loggedIn ? route.fulfill({ json: ALICE }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/tunes/tune-2', (route) => route.fulfill({ json: KESH }));
	await page.route('**/api/tunes/tune-2/comments', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/tunes/tune-2/performances', (route) =>
		route.fulfill({ json: performances })
	);
	await page.route('**/api/artists/*/members', (route) => {
		const id = new URL(route.request().url()).pathname.split('/')[3];
		const { members } = route.request().postDataJSON();
		state.artists[id].members = members.map((name) => ({ id: `m-${name}`, name }));
		return route.fulfill({ json: state.artists[id] });
	});
	await page.route(/\/api\/artists\/[^/]+$/, (route) => {
		const id = new URL(route.request().url()).pathname.split('/')[3];
		if (route.request().method() === 'PUT') {
			state.artists[id].kind = route.request().postDataJSON().kind;
		}
		return route.fulfill({ json: state.artists[id] });
	});
	return state;
}

const altan = {
	id: 'artist-altan',
	name: 'Altan',
	kind: 'band',
	members: [{ id: 'm1', name: 'Mairéad' }],
	bands: []
};
const brock = { id: 'artist-brock', name: 'Paul Brock', kind: 'musician', members: [], bands: [] };

test('musicians and bands, a band with its members', async ({ page }) => {
	await mockApi(page, {
		artists: { [altan.id]: altan, [brock.id]: brock },
		performances: [performance('t1', 'Knees Up', altan), performance('t2', 'Mossy Banks', brock)]
	});
	await page.goto('/tunes/tune-2');

	const section = page.getByRole('region', { name: 'Recorded by' });
	const items = section.getByTestId('recorded-by-artist');
	await expect(items).toHaveCount(2);
	await expect(items.nth(0)).toContainText('Altan');
	await expect(items.nth(0)).toContainText('Band');
	await expect(items.nth(0).getByRole('list', { name: 'Altan members' })).toContainText('Mairéad');
	await expect(items.nth(1)).toContainText('Paul Brock');
	await expect(items.nth(1)).toContainText('Musician');
});

test('the same Artist appears once', async ({ page }) => {
	await mockApi(page, {
		artists: { [altan.id]: altan },
		performances: [performance('t1', 'Knees Up', altan), performance('t2', 'Mossy Banks', altan)]
	});
	await page.goto('/tunes/tune-2');

	await expect(page.getByTestId('recorded-by-artist')).toHaveCount(1);
});

test('no Recorded by list without an Artist', async ({ page }) => {
	await mockApi(page, { artists: {}, performances: [performance('t1', 'Knees Up', null)] });
	await page.goto('/tunes/tune-2');

	await expect(page.getByRole('heading', { name: 'The Kesh' })).toBeVisible();
	await expect(page.getByRole('region', { name: 'Recorded by' })).toHaveCount(0);
});

test('an Account classifies an Artist and adds a member', async ({ page }) => {
	const unclassified = { id: 'artist-altan', name: 'Altan', kind: null, members: [], bands: [] };
	await mockApi(page, {
		loggedIn: true,
		artists: { [unclassified.id]: unclassified },
		performances: [performance('t1', 'Knees Up', { id: unclassified.id, name: 'Altan' })]
	});
	await page.goto('/tunes/tune-2');

	await page.getByLabel('Kind of Altan').selectOption('band');
	await page.getByLabel('Members of Altan').fill('Mairéad');
	await page.getByRole('button', { name: 'Save members' }).click();

	const item = page.getByTestId('recorded-by-artist');
	await expect(item).toContainText('Band');
	await expect(item.getByRole('list', { name: 'Altan members' })).toContainText('Mairéad');
});

test('a visitor is offered no controls', async ({ page }) => {
	await mockApi(page, {
		artists: { [altan.id]: altan },
		performances: [performance('t1', 'Knees Up', altan)]
	});
	await page.goto('/tunes/tune-2');

	await expect(page.getByTestId('recorded-by-artist')).toContainText('Altan');
	await expect(page.getByLabel('Kind of Altan')).toHaveCount(0);
	await expect(page.getByLabel('Members of Altan')).toHaveCount(0);
});
