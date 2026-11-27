import * as api from './api.js';
import { toast } from './ui/toast.svelte.js';
import type { Track } from '../routes/tracks/Tracks.js';

/** A playlist without its tracks, as the API lists it. */
export interface PlaylistSummary {
	id: string;
	name: string;
	description?: string | null;
	trackCount: number;
	updatedAt: string;
}

/** A playlist with its tracks in order. */
export interface Playlist extends PlaylistSummary {
	tracks: Track[];
}

/** The server refused a change to a playlist; `status` is its HTTP status. */
export class PlaylistError extends Error {
	constructor(readonly status: number) {
		super(
			status === 400
				? 'A playlist needs a name of up to 80 characters.'
				: status === 401
					? 'Your session has expired. Log in again.'
					: status === 404
						? 'This playlist or track no longer exists.'
						: status === 409
							? 'The playlist changed meanwhile. Try again.'
							: `The server refused the change (${status}).`
		);
	}
}

/** The API of playlists, one call each. Throws a {@link PlaylistError} when the server refuses. */
export class PlaylistService {
	readonly #fetch: typeof fetch | undefined;

	constructor(fetchFunc?: typeof fetch) {
		this.#fetch = fetchFunc;
	}

	async #call(method: string, path: string, body?: unknown): Promise<Response> {
		const res = await api.request(this.#fetch ?? fetch, method, path, body);
		if (!res.ok) throw new PlaylistError(res.status);
		return res;
	}

	async list(): Promise<PlaylistSummary[]> {
		return (await this.#call('GET', 'playlists')).json();
	}

	async get(id: string): Promise<Playlist> {
		return (await this.#call('GET', `playlists/${encodeURIComponent(id)}`)).json();
	}

	async create(name: string, description?: string): Promise<PlaylistSummary> {
		return (
			await this.#call('POST', 'playlists', { name, description: description || null })
		).json();
	}

	async rename(id: string, name: string, description?: string | null): Promise<PlaylistSummary> {
		return (
			await this.#call('PUT', `playlists/${encodeURIComponent(id)}`, {
				name,
				description: description || null
			})
		).json();
	}

	async delete(id: string): Promise<void> {
		await this.#call('DELETE', `playlists/${encodeURIComponent(id)}`);
	}

	async addTrack(id: string, trackId: string): Promise<void> {
		await this.#call(
			'PUT',
			`playlists/${encodeURIComponent(id)}/tracks/${encodeURIComponent(trackId)}`
		);
	}

	async removeTrack(id: string, trackId: string): Promise<void> {
		await this.#call(
			'DELETE',
			`playlists/${encodeURIComponent(id)}/tracks/${encodeURIComponent(trackId)}`
		);
	}

	async reorder(id: string, trackIds: string[]): Promise<void> {
		await this.#call('PUT', `playlists/${encodeURIComponent(id)}/tracks`, trackIds);
	}
}

/**
 * The logged-in user's playlists, as a list: one copy for the whole app, so that the "Add to playlist" menu, the
 * playlists page and the account page always agree. An anonymous visitor has none.
 */
export class PlaylistState {
	items = $state.raw<PlaylistSummary[]>([]);
	/** Whether the server has answered at least once, so that "none" can be told from "not known yet". */
	loaded = $state(false);

	#loads = 0;
	readonly service: PlaylistService;

	constructor(fetchFunc?: typeof fetch) {
		this.service = new PlaylistService(fetchFunc);
	}

	/** Fetches the playlists. A failure keeps what is shown, and the latest fetch wins. Returns whether it worked. */
	async load(): Promise<boolean> {
		const mine = ++this.#loads;
		try {
			const items = await this.service.list();
			if (mine !== this.#loads) return false;
			this.items = items;
			this.loaded = true;
			return true;
		} catch (e) {
			if (mine === this.#loads && e instanceof PlaylistError && e.status === 401) this.clear();
			return false;
		}
	}

	/** Forgets the playlists, as when the user logs out. */
	clear() {
		this.#loads++;
		this.items = [];
		this.loaded = false;
	}

	/** Creates a playlist; null, with the user told, when the server refuses. */
	async create(name: string, description?: string): Promise<PlaylistSummary | null> {
		try {
			const created = await this.service.create(name.trim(), description?.trim());
			void this.load();
			return created;
		} catch (e) {
			toast.error(errorMessage(e, 'Could not create the playlist. Try again.'));
			return null;
		}
	}

	/** Adds the track to the playlist and says so; returns whether it worked. */
	async addTrack(
		playlist: PlaylistSummary,
		track: { id: string; title: string }
	): Promise<boolean> {
		try {
			await this.service.addTrack(playlist.id, track.id);
			toast.show(`Added “${track.title}” to ${playlist.name}.`);
			void this.load();
			return true;
		} catch (e) {
			toast.error(errorMessage(e, 'Could not add the track to the playlist. Try again.'));
			return false;
		}
	}
}

function errorMessage(e: unknown, fallback: string): string {
	return e instanceof PlaylistError ? e.message : fallback;
}

/** The browser's playlists. */
export const playlists = new PlaylistState();
