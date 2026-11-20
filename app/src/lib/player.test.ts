import { describe, expect, it } from 'vitest';
import { PlayerState, RESTART_THRESHOLD } from './player.svelte.js';

const KNEES_UP = { id: 'track-1', title: 'Knees Up', owner: { displayName: 'Alice Liddell' } };
const REEL = { id: 'track-2', title: 'Reel', owner: { displayName: 'Bob' } };
const JIG = { id: 'track-3', title: 'Jig', owner: null };
const LIST = [KNEES_UP, REEL, JIG];

/** A storage that keeps items in memory. */
function memoryStorage(initial: Record<string, string> = {}) {
	const items = new Map(Object.entries(initial));
	return {
		getItem: (key: string) => items.get(key) ?? null,
		setItem: (key: string, value: string) => void items.set(key, value),
		items
	};
}

const newPlayer = () => new PlayerState(memoryStorage());

describe('playFrom', () => {
	it('loads the track, with the list as queue, and plays it', () => {
		const player = newPlayer();

		player.playFrom(LIST, 1);

		expect(player.source).toEqual({ trackId: 'track-2', start: 0 });
		expect(player.track).toBe(REEL);
		expect(player.queue).toBe(LIST);
		expect(player.index).toBe(1);
		expect(player.paused).toBe(false);
	});

	it('pauses and resumes the loaded track instead of reloading it', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);
		player.currentTime = 30;
		const source = player.source;

		player.playFrom(LIST, 0);
		expect(player.paused).toBe(true);
		player.playFrom(LIST, 0);

		expect(player.paused).toBe(false);
		expect(player.source).toBe(source);
		expect(player.currentTime).toBe(30);
	});

	it('starts another track at a share of its duration once the duration is known', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);

		player.playFrom(LIST, 1, 0.5);
		expect(player.isLoaded('track-2')).toBe(true);
		player.metadataLoaded(100);

		expect(player.currentTime).toBe(50);
	});

	it('seeks within the loaded track at a share of its duration', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);
		player.metadataLoaded(200);

		player.playFrom(LIST, 0, 0.75);

		expect(player.currentTime).toBe(150);
		expect(player.paused).toBe(false);
	});
});

describe('next', () => {
	it('loads the next track of the queue', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);

		player.next();

		expect(player.isLoaded('track-2')).toBe(true);
		expect(player.track).toBe(REEL);
	});

	it('is disabled on the last track', () => {
		const player = newPlayer();
		player.playFrom(LIST, 2);

		expect(player.hasNext).toBe(false);
		player.next();
		expect(player.isLoaded('track-3')).toBe(true);
	});

	it('is disabled for a track opened from a link, which has no queue', () => {
		const player = newPlayer();

		player.play('track-1', 90);

		expect(player.source).toEqual({ trackId: 'track-1', start: 90 });
		expect(player.queue).toEqual([]);
		expect(player.hasNext).toBe(false);
	});

	it('plays when the track ends', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);

		player.ended();

		expect(player.isPlaying('track-2')).toBe(true);
	});

	it('stops at the end of the last track', () => {
		const player = newPlayer();
		player.playFrom(LIST, 2);

		player.ended();

		expect(player.paused).toBe(true);
		expect(player.isLoaded('track-3')).toBe(true);
	});
});

describe('previous', () => {
	it(`restarts a track that has played more than ${RESTART_THRESHOLD} seconds`, () => {
		const player = newPlayer();
		player.playFrom(LIST, 1);
		player.metadataLoaded(100);
		player.currentTime = 20;

		player.previous();

		expect(player.isLoaded('track-2')).toBe(true);
		expect(player.currentTime).toBe(0);
	});

	it('loads the previous track near the start of a track', () => {
		const player = newPlayer();
		player.playFrom(LIST, 1);
		player.currentTime = 1;

		player.previous();

		expect(player.isLoaded('track-1')).toBe(true);
	});

	it('restarts the first track of the queue', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);
		player.currentTime = 1;

		player.previous();

		expect(player.isLoaded('track-1')).toBe(true);
		expect(player.currentTime).toBe(0);
	});
});

describe('progressOf', () => {
	it('is the share played of the loaded track, and 0 for any other', () => {
		const player = newPlayer();
		player.playFrom(LIST, 0);
		player.metadataLoaded(200);
		player.currentTime = 50;

		expect(player.progressOf('track-1')).toBe(0.25);
		expect(player.progressOf('track-2')).toBe(0);
	});
});

describe('volume', () => {
	it('is remembered for the next visit', () => {
		const storage = memoryStorage();
		new PlayerState(storage).setVolume(0.3);

		expect(new PlayerState(storage).volume).toBe(0.3);
	});

	it('remembers muting, and setting the volume unmutes', () => {
		const storage = memoryStorage();
		const player = new PlayerState(storage);

		player.toggleMute();
		expect(new PlayerState(storage).muted).toBe(true);

		player.setVolume(0.5);
		expect(player.muted).toBe(false);
		expect(new PlayerState(storage).muted).toBe(false);
	});

	it('defaults to full volume, unmuted, when nothing usable is saved', () => {
		const player = new PlayerState(memoryStorage({ 'tunelad.player': 'not json' }));

		expect(player.volume).toBe(1);
		expect(player.muted).toBe(false);
	});
});
