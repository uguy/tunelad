import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FavouriteState } from './favourites.svelte.js';
import { toast } from './ui/toast.svelte.js';
import type { Track } from '../routes/tracks/Tracks.js';

const track = (id: string, title = id): Track => ({
	id,
	title,
	description: '',
	format: 'MP3',
	tags: [],
	owner: null
});

type Handler = (method: string, path: string) => Response | Promise<Response>;

function server(handler: Handler) {
	const calls: string[] = [];
	const fetchFunc = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
		const path = String(input);
		calls.push(`${init?.method ?? 'GET'} ${path}`);
		return handler(init?.method ?? 'GET', path);
	}) as unknown as typeof fetch;
	return { fetchFunc, calls };
}

beforeEach(() => {
	toast.messages = [];
	vi.useFakeTimers();
});
afterEach(() => vi.useRealTimers());

describe('load', () => {
	it('keeps the tracks the server answers with, and their ids', async () => {
		const { fetchFunc } = server(() => Response.json([track('a'), track('b')]));
		const state = new FavouriteState(fetchFunc);

		expect(await state.load()).toBe(true);

		expect(state.tracks.map((t) => t.id)).toEqual(['a', 'b']);
		expect(state.has('a')).toBe(true);
		expect(state.has('c')).toBe(false);
		expect(state.loaded).toBe(true);
	});

	it('has no favourites for an anonymous visitor (401)', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 401 }));
		const state = new FavouriteState(fetchFunc);
		state.tracks = [track('a')];

		await state.load();

		expect(state.tracks).toEqual([]);
		expect(state.has('a')).toBe(false);
	});

	it('keeps what it has when the server fails', async () => {
		const answers = [Response.json([track('a')]), new Response(null, { status: 500 })];
		const { fetchFunc } = server(() => answers.shift()!);
		const state = new FavouriteState(fetchFunc);
		await state.load();

		expect(await state.load()).toBe(false);

		expect(state.tracks.map((t) => t.id)).toEqual(['a']);
	});

	it('ignores an answer that comes after a later fetch was made', async () => {
		let release!: (r: Response) => void;
		const slow = new Promise<Response>((resolve) => (release = resolve));
		const answers = [slow, Promise.resolve(Response.json([track('new')]))];
		const { fetchFunc } = server(() => answers.shift()!);
		const state = new FavouriteState(fetchFunc);

		const first = state.load();
		await state.load();
		release(Response.json([track('old')]));
		await first;

		expect(state.tracks.map((t) => t.id)).toEqual(['new']);
	});

	it('forgets everything on clear', async () => {
		const { fetchFunc } = server(() => Response.json([track('a')]));
		const state = new FavouriteState(fetchFunc);
		await state.load();

		state.clear();

		expect(state.tracks).toEqual([]);
		expect(state.loaded).toBe(false);
	});
});

describe('toggle', () => {
	it('marks a track: the heart is on at once, and the server is told', async () => {
		let release!: (r: Response) => void;
		const { fetchFunc, calls } = server((method) =>
			method === 'PUT'
				? new Promise<Response>((resolve) => (release = resolve))
				: Response.json([track('a')])
		);
		const state = new FavouriteState(fetchFunc);

		const done = state.toggle('a');
		expect(state.has('a')).toBe(true);
		release(new Response(null, { status: 204 }));

		expect(await done).toBe(true);
		expect(calls[0]).toBe('PUT /api/tracks/a/favourite');
	});

	it('unmarks a track: it leaves the list at once', async () => {
		const { fetchFunc, calls } = server((method) =>
			method === 'DELETE' ? new Response(null, { status: 204 }) : Response.json([track('a')])
		);
		const state = new FavouriteState(fetchFunc);
		await state.load();

		const done = state.toggle('a');
		expect(state.has('a')).toBe(false);
		expect(state.tracks).toEqual([]);

		expect(await done).toBe(true);
		expect(calls).toContain('DELETE /api/tracks/a/favourite');
	});

	it('goes back and tells the user when the server refuses', async () => {
		const { fetchFunc } = server((method) =>
			method === 'PUT' ? new Response(null, { status: 500 }) : Response.json([])
		);
		const state = new FavouriteState(fetchFunc);

		expect(await state.toggle('a')).toBe(false);

		expect(state.has('a')).toBe(false);
		expect(toast.messages.at(-1)).toMatchObject({ kind: 'error' });
		expect(toast.messages.at(-1)?.text).toContain('Could not add');
	});

	it('goes back when the connection fails', async () => {
		const fetchFunc = vi.fn(async () => {
			throw new TypeError('network');
		}) as unknown as typeof fetch;
		const state = new FavouriteState(fetchFunc);
		state.ids.add('a');

		expect(await state.toggle('a')).toBe(false);

		expect(state.has('a')).toBe(true);
		expect(toast.messages.at(-1)?.text).toContain('Could not remove');
	});

	it('asks to log in again when the session has expired', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 401 }));
		const state = new FavouriteState(fetchFunc);

		await state.toggle('a');

		expect(toast.messages.at(-1)?.text).toContain('Log in again');
	});
});
