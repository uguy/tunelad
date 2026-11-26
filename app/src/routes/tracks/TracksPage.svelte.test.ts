import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { live } from '#lib/live.svelte.js';
import { FakeEventSource } from '#lib/live.testing.js';
import TracksPage from './+page.svelte';
import type { Track } from './Tracks.js';

// The page reads the SvelteKit page state (the track just uploaded, the deep link) and resolves its links
const pageState = vi.hoisted(() => ({
	url: new URL('http://localhost/tracks'),
	state: {} as { uploaded?: unknown }
}));
vi.mock('$app/state', () => ({ page: pageState }));
vi.mock('$app/paths', () => ({ resolve: (path: string) => path }));

const track = (id: string, title: string): Track => ({
	id,
	title,
	description: `${title}, described`,
	artist: null,
	album: null,
	format: 'MP3',
	tags: [],
	owner: { id: 'account-bob', displayName: 'Bob Smith' }
});
const KNEES_UP = track('track-1', 'Knees Up');
const MOSSY_BANKS = track('track-2', 'Mossy Banks');
const RAIN = track('track-3', 'Rain on tin');

/** The searches the page made, and what the server answers to the next ones, by search text. */
let searches: string[];
let answers: Record<string, Track[] | 'error'>;

beforeEach(() => {
	pageState.state = {};
	searches = [];
	answers = { '': [KNEES_UP, MOSSY_BANKS] };
	FakeEventSource.instances = [];
	vi.stubGlobal('EventSource', FakeEventSource);
	vi.stubGlobal(
		'fetch',
		vi.fn(async (input: RequestInfo | URL) => {
			const url = new URL(String(input), 'http://localhost');
			if (url.pathname === '/api/tracks/search') {
				const text = url.searchParams.get('q') ?? '';
				searches.push(text);
				const answer = answers[text] ?? [];
				return answer === 'error' ? new Response(null, { status: 500 }) : Response.json(answer);
			}
			// Waveforms are not under test here
			return Response.json({ status: 'FAILED', peaks: [] });
		})
	);
	live.open();
});

afterEach(() => {
	live.close();
	vi.unstubAllGlobals();
});

const source = () => FakeEventSource.instances[0];
const indexed = (id: string) => source().emit('track.indexed', { type: 'track.indexed', id });
const unindexed = (id: string) => source().emit('track.unindexed', { type: 'track.unindexed', id });

async function showTracks() {
	const screen = await render(TracksPage, { data: { tracks: [KNEES_UP, MOSSY_BANKS] } } as never);
	await expect.element(page.getByText('Knees Up')).toBeVisible();
	return screen;
}

/** Searches for the text, the way a visitor does. */
async function search(text: string) {
	await page.getByRole('searchbox').fill(text);
	await page.getByRole('button', { name: 'search-track-btn' }).click();
	await vi.waitFor(() => expect(searches).toContain(text));
}

describe('the tracks list and live updates', () => {
	it('shows a track another user uploaded, once it is searchable, and shows it once', async () => {
		await showTracks();
		answers[''] = [RAIN, KNEES_UP, MOSSY_BANKS];

		indexed(RAIN.id);

		await expect.element(page.getByText('Rain on tin')).toBeVisible();
		expect(page.getByText('Rain on tin').elements()).toHaveLength(1);
		expect(page.getByText('Knees Up').elements()).toHaveLength(1);
	});

	it('shows a track the visitor just uploaded once, when the live update for it arrives', async () => {
		// The upload page hands the new track over, on top of the list, until search lists it
		pageState.state = { uploaded: RAIN };
		await render(TracksPage, { data: { tracks: [KNEES_UP, MOSSY_BANKS] } } as never);
		await expect.element(page.getByText('Rain on tin')).toBeVisible();
		answers[''] = [RAIN, KNEES_UP, MOSSY_BANKS];

		indexed(RAIN.id);
		await vi.waitFor(() => expect(searches).toHaveLength(1));
		await new Promise((resolve) => setTimeout(resolve, 100));

		await expect.element(page.getByText('Rain on tin')).toBeVisible();
		expect(page.getByText('Rain on tin').elements()).toHaveLength(1);
	});

	it('keeps the track the visitor just uploaded when a refetch does not list it yet', async () => {
		pageState.state = { uploaded: RAIN };
		await render(TracksPage, { data: { tracks: [KNEES_UP, MOSSY_BANKS] } } as never);
		await expect.element(page.getByText('Rain on tin')).toBeVisible();

		// Another user's change arrives before search has caught up with the upload
		indexed('track-9');
		await vi.waitFor(() => expect(searches).toHaveLength(1));
		await new Promise((resolve) => setTimeout(resolve, 100));

		expect(page.getByText('Rain on tin').elements()).toHaveLength(1);
	});

	it('shows the new title of a track another user edited', async () => {
		await showTracks();
		answers[''] = [track('track-1', 'Knees Up (live)'), MOSSY_BANKS];

		indexed(KNEES_UP.id);

		await expect.element(page.getByText('Knees Up (live)')).toBeVisible();
		expect(page.getByText('Knees Up', { exact: true }).elements()).toHaveLength(0);
	});

	it('drops a track another user deleted', async () => {
		await showTracks();
		answers[''] = [MOSSY_BANKS];

		unindexed(KNEES_UP.id);

		await expect.element(page.getByText('Mossy Banks')).toBeVisible();
		await vi.waitFor(() => expect(page.getByText('Knees Up').elements()).toHaveLength(0));
	});

	it('makes one request for a burst of events', async () => {
		await showTracks();

		for (let i = 0; i < 10; i++) indexed(`track-${i}`);
		await vi.waitFor(() => expect(searches).toHaveLength(1));
		await new Promise((resolve) => setTimeout(resolve, 600));

		expect(searches).toEqual(['']);
	});

	it('refetches with the search text of the results shown', async () => {
		await showTracks();
		answers['rain'] = [RAIN];
		await search('rain');
		await expect.element(page.getByText('Rain on tin')).toBeVisible();
		searches.length = 0;
		answers['rain'] = [RAIN, track('track-4', 'Rain again')];

		indexed('track-4');

		await expect.element(page.getByText('Rain again')).toBeVisible();
		expect(searches).toEqual(['rain']);
	});

	it('does not use text that was typed and not searched', async () => {
		await showTracks();
		answers['rain'] = [RAIN];
		await search('rain');
		await page.getByRole('searchbox').fill('sun');
		searches.length = 0;
		answers['rain'] = [RAIN, track('track-4', 'Rain again')];

		indexed('track-4');

		await expect.element(page.getByText('Rain again')).toBeVisible();
		expect(searches).toEqual(['rain']);
		await expect.element(page.getByRole('searchbox')).toHaveValue('sun');
	});

	it('keeps the list when the refetch fails', async () => {
		await showTracks();
		answers[''] = 'error';

		indexed(RAIN.id);
		await vi.waitFor(() => expect(searches).toHaveLength(1));
		await new Promise((resolve) => setTimeout(resolve, 100));

		await expect.element(page.getByText('Knees Up')).toBeVisible();
		await expect.element(page.getByText('Mossy Banks')).toBeVisible();
	});

	it('refetches when the connection is back after a failure', async () => {
		await showTracks();
		source().open();
		answers[''] = [RAIN, KNEES_UP, MOSSY_BANKS];

		source().fail();
		source().open();

		await expect.element(page.getByText('Rain on tin')).toBeVisible();
	});
});
