import { SvelteSet } from 'svelte/reactivity';
import * as api from './api.js';
import { toast } from './ui/toast.svelte.js';
import type { Track } from '../routes/tracks/Tracks.js';

/**
 * The logged-in user's favourite tracks. One copy for the whole app, so that the heart of a track, the player bar,
 * the favourites page and the account page always agree. An anonymous visitor has none.
 */
export class FavouriteState {
	/** The favourite tracks, most recently marked first. */
	tracks = $state.raw<Track[]>([]);
	/** Their ids, for the hearts: updated as soon as a heart is clicked, before the server answers. */
	readonly ids = new SvelteSet<string>();
	/** Whether the server has answered at least once, so that "none" can be told from "not known yet". */
	loaded = $state(false);

	#loads = 0;
	readonly #fetch: typeof fetch | undefined;

	/** `fetchFunc` is the fetch to use: the browser's by default. */
	constructor(fetchFunc?: typeof fetch) {
		this.#fetch = fetchFunc;
	}

	get #request() {
		return this.#fetch ?? fetch;
	}

	/** Whether that track is one of the user's favourites. */
	has(trackId: string): boolean {
		return this.ids.has(trackId);
	}

	/**
	 * Fetches the favourites. A failure keeps what is shown, and the latest fetch wins when several are in flight.
	 * Returns whether the favourites are now what the server says.
	 */
	async load(): Promise<boolean> {
		const mine = ++this.#loads;
		try {
			const res = await api.request(this.#request, 'GET', 'me/favourites');
			if (mine !== this.#loads) return false;
			if (res.status === 401) {
				this.clear();
				return true;
			}
			if (!res.ok) return false;
			this.#set(await res.json());
			return true;
		} catch {
			return false;
		}
	}

	/** Forgets the favourites, as when the user logs out. */
	clear() {
		this.#loads++;
		this.#set([]);
		this.loaded = false;
	}

	#set(tracks: Track[]) {
		this.tracks = tracks;
		this.ids.clear();
		tracks.forEach((t) => this.ids.add(t.id));
		this.loaded = true;
	}

	/**
	 * Marks the track as a favourite, or unmarks it. The heart changes at once; when the server refuses, it changes
	 * back and the user is told. Returns whether the server accepted.
	 */
	async toggle(trackId: string): Promise<boolean> {
		const marking = !this.has(trackId);
		this.#setId(trackId, marking);
		try {
			const res = await api.request(
				this.#request,
				marking ? 'PUT' : 'DELETE',
				`tracks/${encodeURIComponent(trackId)}/favourite`
			);
			if (!res.ok) throw new Error(String(res.status));
		} catch (e) {
			this.#setId(trackId, !marking);
			toast.error(
				(e as Error).message === '401'
					? 'Your session has expired. Log in again to keep favourites.'
					: marking
						? 'Could not add this track to your favourites. Try again.'
						: 'Could not remove this track from your favourites. Try again.'
			);
			return false;
		}
		// The list shows the track as the server has it: marked tracks come first, unmarked ones are gone
		void this.load();
		return true;
	}

	#setId(trackId: string, marked: boolean) {
		if (marked) this.ids.add(trackId);
		else this.ids.delete(trackId);
		if (!marked) this.tracks = this.tracks.filter((t) => t.id !== trackId);
	}
}

/** The browser's favourites. */
export const favourites = new FavouriteState();
