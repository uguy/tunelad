import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { live } from '#lib/live.svelte.js';
import { FakeEventSource } from '#lib/live.testing.js';
import { player } from '#lib/player.svelte.js';
import type { Audio, Track } from '../Tracks.js';
import TrackList from './TrackList.svelte';

const track = (id: string, title: string, format = 'WMA'): Track => ({
	id,
	title,
	description: `${title}, described`,
	artist: null,
	album: null,
	format,
	tags: [],
	owner: { id: 'account-bob', displayName: 'Bob Smith' }
});

/** The paths asked for, and the audio state the server answers for each track. */
let asked: string[];
let states: Record<string, Audio['status']>;

beforeEach(() => {
	asked = [];
	states = {};
	FakeEventSource.instances = [];
	player.source = null;
	vi.stubGlobal('EventSource', FakeEventSource);
	vi.stubGlobal(
		'fetch',
		vi.fn(async (input: RequestInfo | URL) => {
			const path = new URL(String(input), 'http://localhost').pathname;
			asked.push(path);
			const [, , , id, what] = path.split('/');
			return what === 'audio'
				? Response.json({ status: states[id] ?? 'READY' })
				: Response.json({ status: 'PENDING', peaks: [] });
		})
	);
	live.open();
});

afterEach(() => {
	live.close();
	vi.restoreAllMocks();
	vi.unstubAllGlobals();
});

const prepared = (id: string) =>
	FakeEventSource.instances[0].emit('audio.prepared', { type: 'audio.prepared', id });
const audioAsks = (id: string) => asked.filter((path) => path === `/api/tracks/${id}/audio`).length;
const playButton = (title: string) =>
	page.getByRole('button', { name: `play track ${title} button` });

describe('audio of the tracks the server converts', () => {
	it('shows that the audio is being prepared and offers no play action', async () => {
		states['track-1'] = 'PENDING';

		await render(TrackList, { tracks: [track('track-1', 'Touching Cloth')] });

		await expect
			.element(page.getByRole('status').filter({ hasText: 'Preparing audio' }))
			.toBeVisible();
		await expect.element(playButton('Touching Cloth')).toBeDisabled();
	});

	it('makes the track playable when the server says the audio is prepared, without polling', async () => {
		states['track-1'] = 'PENDING';
		await render(TrackList, { tracks: [track('track-1', 'Touching Cloth')] });
		await expect.element(page.getByTestId('audio-status')).toBeVisible();
		await vi.waitFor(() => expect(audioAsks('track-1')).toBe(1));

		states['track-1'] = 'READY';
		prepared('track-1');

		await expect.element(playButton('Touching Cloth')).toBeEnabled();
		await expect.element(page.getByTestId('audio-status')).not.toBeInTheDocument();
		expect(audioAsks('track-1')).toBe(2);
	});

	it('says in text that the audio could not be prepared, with no play action but with edit and delete', async () => {
		states['track-1'] = 'FAILED';
		const { session } = await import('#lib/session.svelte.js');
		session.account = { id: 'account-bob', displayName: 'Bob Smith' } as typeof session.account;

		await render(TrackList, { tracks: [track('track-1', 'Touching Cloth')] });

		await expect
			.element(page.getByRole('status').filter({ hasText: 'Audio could not be prepared' }))
			.toBeVisible();
		await expect.element(playButton('Touching Cloth')).toBeDisabled();
		await expect
			.element(page.getByRole('button', { name: 'edit track Touching Cloth' }))
			.toBeVisible();
		await expect
			.element(page.getByRole('button', { name: 'delete track Touching Cloth' }))
			.toBeVisible();
		session.account = null;
	});

	it('does not ask about tracks in a format browsers play, which stay playable', async () => {
		await render(TrackList, { tracks: [track('track-1', 'Knees Up', 'MP3')] });

		await expect.element(playButton('Knees Up')).toBeEnabled();
		expect(audioAsks('track-1')).toBe(0);
	});

	it('does not start the player on a track that is not ready', async () => {
		states['track-1'] = 'PENDING';
		await render(TrackList, { tracks: [track('track-1', 'Touching Cloth')] });
		await expect.element(page.getByTestId('audio-status')).toBeVisible();

		await playButton('Touching Cloth').click({ force: true });

		expect(player.source).toBeNull();
	});

	it('ignores events for tracks it does not show', async () => {
		await render(TrackList, { tracks: [track('track-1', 'Touching Cloth')] });
		await vi.waitFor(() => expect(audioAsks('track-1')).toBe(1));

		prepared('track-unknown');
		await new Promise((resolve) => setTimeout(resolve, 100));

		expect(asked.filter((path) => path.endsWith('/audio'))).toEqual(['/api/tracks/track-1/audio']);
	});
});
