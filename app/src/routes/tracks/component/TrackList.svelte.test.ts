import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { live } from '#lib/live.svelte.js';
import { FakeEventSource } from '#lib/live.testing.js';
import type { Track, Waveform } from '../Tracks.js';
import TrackList from './TrackList.svelte';

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

const PENDING: Waveform = { status: 'PENDING', peaks: [] };
const READY: Waveform = {
	status: 'READY',
	peaks: Array.from({ length: 200 }, (_, i) => (i % 10) / 10)
};

/** The ids of the tracks whose waveform was asked for, in order, and what the server answers to the next ask. */
let asked: string[];
let answer: (id: string) => Promise<Waveform> | Waveform;

beforeEach(() => {
	asked = [];
	answer = () => PENDING;
	FakeEventSource.instances = [];
	vi.stubGlobal('EventSource', FakeEventSource);
	vi.stubGlobal(
		'fetch',
		vi.fn(async (input: RequestInfo | URL) => {
			const id = new URL(String(input), 'http://localhost').pathname.split('/')[3];
			asked.push(id);
			return Response.json(await answer(id));
		})
	);
	live.open();
});

afterEach(() => {
	live.close();
	vi.restoreAllMocks();
	vi.unstubAllGlobals();
});

const source = () => FakeEventSource.instances[0];
const computed = (id: string) =>
	source().emit('waveform.computed', { type: 'waveform.computed', id });
const asks = (id: string) => asked.filter((asked) => asked === id).length;

describe('waveforms of the tracks list', () => {
	it('replaces the placeholder as soon as the server says the waveform is computed', async () => {
		const screen = await render(TrackList, { tracks: [track('track-1', 'Knees Up')] });
		await vi.waitFor(() => expect(asks('track-1')).toBe(1));
		await expect.element(page.getByTestId('waveform-placeholder')).toBeVisible();

		answer = () => READY;
		computed('track-1');

		await expect.element(page.getByTestId('waveform')).toBeVisible();
		expect(screen.container.querySelector('[data-testid="waveform-placeholder"]')).toBeNull();
		expect(asks('track-1')).toBe(2);
	});

	// The polling gave up after 30 seconds, so a waveform computed later (the startup backfill, say) never showed.
	// An event has no deadline, and nothing waits on a timer any more.
	it('does not poll: no timer asks again for a waveform that is still pending', async () => {
		const timers = vi.spyOn(globalThis, 'setTimeout');
		await render(TrackList, { tracks: [track('track-1', 'Knees Up')] });
		await vi.waitFor(() => expect(asks('track-1')).toBe(1));
		await new Promise((resolve) => setTimeout(resolve, 200));

		// The polling this replaces waited 3 seconds between attempts
		const polling = timers.mock.calls.filter(
			([, delay]) => typeof delay === 'number' && delay >= 3000
		);
		expect(polling).toHaveLength(0);
		expect(asks('track-1')).toBe(1);
	});

	it('does not fetch the waveform of a track that is not on screen, whatever the server says', async () => {
		const tracks = Array.from({ length: 40 }, (_, i) => track(`track-${i}`, `Tune ${i}`));
		await render(TrackList, { tracks });
		await vi.waitFor(() => expect(asks('track-0')).toBe(1));

		computed('track-39');
		await new Promise((resolve) => setTimeout(resolve, 200));

		expect(asks('track-39')).toBe(0);
	});

	it('ignores events without an id and for tracks it does not show', async () => {
		await render(TrackList, { tracks: [track('track-1', 'Knees Up')] });
		await vi.waitFor(() => expect(asks('track-1')).toBe(1));

		computed('track-unknown');
		source().emit('waveform.computed', { type: 'waveform.computed' });
		await new Promise((resolve) => setTimeout(resolve, 100));

		expect(asked).toEqual(['track-1']);
	});

	it('keeps the latest answer when an earlier one arrives after it', async () => {
		await render(TrackList, { tracks: [track('track-1', 'Knees Up')] });
		let late!: (waveform: Waveform) => void;
		answer = () => new Promise<Waveform>((resolve) => (late = resolve));
		// Rendering asked once already (answered at once, before `answer` changed): ask again, slowly
		await vi.waitFor(() => expect(asks('track-1')).toBe(1));
		computed('track-1');
		await vi.waitFor(() => expect(asks('track-1')).toBe(2));
		const slow = late;
		answer = () => READY;
		computed('track-1');
		await expect.element(page.getByTestId('waveform')).toBeVisible();

		// The slow answer, from before the waveform was computed, must not bring the placeholder back
		slow(PENDING);
		await new Promise((resolve) => setTimeout(resolve, 100));

		await expect.element(page.getByTestId('waveform')).toBeVisible();
	});

	it('asks again for the waveforms it showed when the connection is back after a failure', async () => {
		await render(TrackList, { tracks: [track('track-1', 'Knees Up')] });
		await vi.waitFor(() => expect(asks('track-1')).toBe(1));
		source().open();
		answer = () => READY;

		source().fail();
		source().open();

		await expect.element(page.getByTestId('waveform')).toBeVisible();
		expect(asks('track-1')).toBe(2);
	});

	it('stops listening when the list goes away', async () => {
		const screen = await render(TrackList, { tracks: [track('track-1', 'Knees Up')] });
		await vi.waitFor(() => expect(asks('track-1')).toBe(1));

		screen.unmount();
		computed('track-1');
		await new Promise((resolve) => setTimeout(resolve, 100));

		expect(asks('track-1')).toBe(1);
	});
});
