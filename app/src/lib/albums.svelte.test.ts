import { describe, expect, it, vi } from 'vitest';
import {
	AlbumError,
	AlbumService,
	AlbumsState,
	catalogueTracksOf,
	formatReleaseDate,
	isBy,
	type Album
} from './albums.svelte.js';
import type { LiveHandler } from './live.svelte.js';
import type { Track } from '../routes/tracks/Tracks.js';

const album = (id: string, title = id): Album => ({ id, title, artist: 'Dervish' });
const track = (id: string, catalogue: boolean): Track => ({
	id,
	title: id,
	format: catalogue ? '' : 'MP3',
	catalogue
});

function server(respond: (method: string, path: string, body?: string) => Response) {
	const calls: string[] = [];
	const fetchFunc = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
		const method = init?.method ?? 'GET';
		calls.push(`${method} ${String(input)}`);
		return respond(method, String(input), init?.body as string | undefined);
	}) as unknown as typeof fetch;
	return { fetchFunc, calls };
}

describe('helpers', () => {
	it('writes a release date the same in every time zone', () => {
		expect(formatReleaseDate('1996-05-01', 'en-GB')).toBe('1 May 1996');
		expect(formatReleaseDate(null)).toBe('');
		expect(formatReleaseDate('not a date')).toBe('not a date');
	});

	it('picks the Catalogue Tracks of an album', () => {
		const tracks = [track('a', true), track('b', false), track('c', true)];
		expect(catalogueTracksOf({ tracks }).map((t) => t.id)).toEqual(['a', 'c']);
		expect(catalogueTracksOf({})).toEqual([]);
	});

	it('compares artists ignoring case and spaces', () => {
		expect(isBy(album('x'), ' dervish ')).toBe(true);
		expect(isBy(album('x'), 'Altan')).toBe(false);
	});
});

describe('AlbumService', () => {
	it('searches with the query encoded', async () => {
		const { fetchFunc, calls } = server(() => Response.json([album('a')]));

		const found = await new AlbumService(fetchFunc).search(' end of the day ');

		expect(found.map((a) => a.id)).toEqual(['a']);
		expect(calls).toEqual(['GET /api/albums/search?q=end%20of%20the%20day']);
	});

	it('creates an album, leaving an empty release date null', async () => {
		const { fetchFunc } = server((_, __, body) => {
			expect(JSON.parse(body ?? '')).toEqual({
				title: 'At the End of the Day',
				artist: 'Dervish',
				releaseDate: null
			});
			return Response.json(album('new'), { status: 201 });
		});

		const created = await new AlbumService(fetchFunc).create({
			title: ' At the End of the Day ',
			artist: 'Dervish ',
			releaseDate: ''
		});

		expect(created.id).toBe('new');
	});

	it('says what a refusal means', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 400 }));

		await expect(
			new AlbumService(fetchFunc).create({ title: '', artist: '' })
		).rejects.toBeInstanceOf(AlbumError);
		await expect(new AlbumService(fetchFunc).create({ title: '', artist: '' })).rejects.toThrow(
			'needs a title'
		);
	});

	it('has no album for a 404', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 404 }));

		expect(await new AlbumService(fetchFunc).get('none')).toBeNull();
	});

	it('lists the albums of a tune, none when it cannot be had', async () => {
		const ok = server(() => Response.json([album('a')]));
		expect(await new AlbumService(ok.fetchFunc).ofTune('t 1')).toHaveLength(1);
		expect(ok.calls).toEqual(['GET /api/tunes/t%201/albums']);

		const failing = server(() => new Response(null, { status: 500 }));
		expect(await new AlbumService(failing.fetchFunc).ofTune('t')).toEqual([]);
	});
});

describe('AlbumsState', () => {
	function fakeLive() {
		const handlers = new Map<string, LiveHandler>();
		const reconnected: (() => void)[] = [];
		return {
			handlers,
			reconnected,
			on: (type: string, handler: LiveHandler) => {
				handlers.set(type, handler);
				return () => handlers.delete(type);
			},
			onReconnect: (handler: () => void) => {
				reconnected.push(handler);
				return () => {};
			}
		};
	}

	it('loads the albums matching the query', async () => {
		const { fetchFunc } = server(() => Response.json([album('a'), album('b')]));
		const state = new AlbumsState(new AlbumService(fetchFunc), fakeLive());

		await state.load('dervish');

		expect(state.albums.map((a) => a.id)).toEqual(['a', 'b']);
		expect(state.status).toBe('ready');
	});

	it('reports a failure and keeps the albums shown', async () => {
		let fail = false;
		const { fetchFunc } = server(() =>
			fail ? new Response(null, { status: 500 }) : Response.json([album('a')])
		);
		const state = new AlbumsState(new AlbumService(fetchFunc), fakeLive());
		await state.load('');
		fail = true;

		await state.load('');

		expect(state.status).toBe('error');
		expect(state.albums).toHaveLength(1);
	});

	it('refetches with the same query when an album changes', async () => {
		vi.useFakeTimers();
		const { fetchFunc, calls } = server(() => Response.json([]));
		const live = fakeLive();
		const state = new AlbumsState(new AlbumService(fetchFunc), live);
		await state.load('dervish');
		const stop = state.follow();

		live.handlers.get('album.changed')?.({ type: 'album.changed' });
		await vi.advanceTimersByTimeAsync(400);

		expect(calls).toEqual(['GET /api/albums/search?q=dervish', 'GET /api/albums/search?q=dervish']);
		stop();
		vi.useRealTimers();
	});
});
