import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlaylistError, PlaylistService, PlaylistState } from './playlists.svelte.js';
import { toast } from './ui/toast.svelte.js';

const summary = (id: string, name = id) => ({
	id,
	name,
	description: null,
	trackCount: 0,
	updatedAt: '2026-11-27T10:00:00Z'
});

function server(
	handler: (method: string, path: string, body?: unknown) => Response | Promise<Response>
) {
	const calls: string[] = [];
	const fetchFunc = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
		const method = init?.method ?? 'GET';
		calls.push(`${method} ${String(input)}`);
		return handler(method, String(input), init?.body ? JSON.parse(String(init.body)) : undefined);
	}) as unknown as typeof fetch;
	return { fetchFunc, calls };
}

beforeEach(() => {
	toast.messages = [];
	vi.useFakeTimers();
});
afterEach(() => vi.useRealTimers());

describe('PlaylistService', () => {
	it('sends the calls of the API, with the ids encoded', async () => {
		const { fetchFunc, calls } = server((method) =>
			method === 'GET' ? Response.json([]) : new Response(null, { status: 204 })
		);
		const service = new PlaylistService(fetchFunc);

		await service.addTrack('p 1', 't/1');
		await service.removeTrack('p1', 't1');
		await service.reorder('p1', ['b', 'a']);
		await service.delete('p1');

		expect(calls).toEqual([
			'PUT /api/playlists/p%201/tracks/t%2F1',
			'DELETE /api/playlists/p1/tracks/t1',
			'PUT /api/playlists/p1/tracks',
			'DELETE /api/playlists/p1'
		]);
	});

	it('sends the order as a JSON array, and a name with an empty description as null', async () => {
		const bodies: unknown[] = [];
		const { fetchFunc } = server((method, path, body) => {
			bodies.push(body);
			return Response.json(summary('p1'));
		});
		const service = new PlaylistService(fetchFunc);

		await service.reorder('p1', ['b', 'a']);
		await service.create('Reels', '');

		expect(bodies).toEqual([['b', 'a'], { name: 'Reels', description: null }]);
	});

	it('throws a PlaylistError with the status when refused', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 404 }));

		await expect(new PlaylistService(fetchFunc).get('p1')).rejects.toMatchObject({
			status: 404,
			message: expect.stringContaining('no longer exists')
		});
		expect(new PlaylistError(409).message).toContain('changed meanwhile');
	});
});

describe('PlaylistState', () => {
	it('keeps the playlists the server lists', async () => {
		const { fetchFunc } = server(() => Response.json([summary('a'), summary('b')]));
		const state = new PlaylistState(fetchFunc);

		expect(await state.load()).toBe(true);

		expect(state.items.map((p) => p.id)).toEqual(['a', 'b']);
		expect(state.loaded).toBe(true);
	});

	it('keeps what it has when the server fails, and forgets on a 401', async () => {
		const answers = [
			Response.json([summary('a')]),
			new Response(null, { status: 500 }),
			new Response(null, { status: 401 })
		];
		const { fetchFunc } = server(() => answers.shift()!);
		const state = new PlaylistState(fetchFunc);
		await state.load();

		expect(await state.load()).toBe(false);
		expect(state.items).toHaveLength(1);

		await state.load();
		expect(state.items).toEqual([]);
	});

	it('creates a playlist, and refetches the list', async () => {
		const { fetchFunc, calls } = server((method) =>
			method === 'POST'
				? Response.json(summary('new', 'Reels'), { status: 201 })
				: Response.json([summary('new', 'Reels')])
		);
		const state = new PlaylistState(fetchFunc);

		const created = await state.create('  Reels ');

		expect(created?.name).toBe('Reels');
		expect(calls).toContain('POST /api/playlists');
		await vi.waitFor(() => expect(state.items.map((p) => p.name)).toEqual(['Reels']));
	});

	it('tells the user when creating fails', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 400 }));
		const state = new PlaylistState(fetchFunc);

		expect(await state.create('x')).toBeNull();

		expect(toast.messages.at(-1)).toMatchObject({ kind: 'error' });
		expect(toast.messages.at(-1)?.text).toContain('needs a name');
	});

	it('adds a track and confirms it', async () => {
		const { fetchFunc, calls } = server((method) =>
			method === 'PUT' ? new Response(null, { status: 204 }) : Response.json([])
		);
		const state = new PlaylistState(fetchFunc);

		const added = await state.addTrack(summary('p1', 'Session tunes'), {
			id: 't1',
			title: 'Knees Up'
		});

		expect(added).toBe(true);
		expect(calls[0]).toBe('PUT /api/playlists/p1/tracks/t1');
		expect(toast.messages.at(-1)).toMatchObject({
			kind: 'info',
			text: 'Added “Knees Up” to Session tunes.'
		});
	});

	it('tells the user when adding fails', async () => {
		const { fetchFunc } = server(() => new Response(null, { status: 404 }));
		const state = new PlaylistState(fetchFunc);

		expect(await state.addTrack(summary('p1'), { id: 't1', title: 'Knees Up' })).toBe(false);

		expect(toast.messages.at(-1)).toMatchObject({ kind: 'error' });
	});
});
