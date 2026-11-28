import * as api from './api.js';
import { live as liveState, type LiveState } from './live.svelte.js';
import { refreshOnLive } from './liveRefresh.js';
import type { Track } from '../routes/tracks/Tracks.js';

/** An album as the API returns it: its tracks come only when it is fetched by id. */
export interface Album {
	id: string;
	title: string;
	artist: string;
	artistId?: string | null;
	coverUrl?: string | null;
	/** An ISO date, absent when unknown. */
	releaseDate?: string | null;
	/** In album order, Catalogue Tracks included; empty in a list. */
	tracks?: Track[];
}

/** A new album as the form sends it: an empty optional field is left out. */
export interface AlbumInput {
	title: string;
	artist: string;
	releaseDate?: string | null;
}

/** The server refused a request about albums; `status` is its HTTP status. */
export class AlbumError extends Error {
	constructor(readonly status: number) {
		super(
			status === 400
				? 'An album needs a title and an artist.'
				: status === 401
					? 'Your session has expired. Log in again to create albums.'
					: status === 403
						? 'You may not create this album. Reload the page and try again.'
						: status === 404
							? 'This album no longer exists.'
							: `The server refused the request (${status}).`
		);
	}
}

/** A release date as a reader writes it ("5 May 1996"), whatever the browser's time zone; empty when unknown. */
export function formatReleaseDate(date?: string | null, locale?: string): string {
	const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date ?? '');
	if (!date || !match) return date ?? '';
	const [, year, month, day] = match.map(Number);
	return new Intl.DateTimeFormat(locale, { dateStyle: 'long', timeZone: 'UTC' }).format(
		Date.UTC(year, month - 1, day)
	);
}

/** The Catalogue Tracks of an album: the places that still wait for audio. */
export function catalogueTracksOf(album: Pick<Album, 'tracks'>): Track[] {
	return (album.tracks ?? []).filter((track) => track.catalogue);
}

/** Whether the album is by that artist, ignoring case and surrounding spaces. */
export function isBy(album: Pick<Album, 'artist'>, artist: string): boolean {
	return album.artist.trim().toLowerCase() === artist.trim().toLowerCase();
}

/** The API of albums, one call each. `fetchFunc` is SvelteKit's in a load function, the global one by default. */
export class AlbumService {
	readonly #fetch: typeof fetch | undefined;

	constructor(fetchFunc?: typeof fetch) {
		this.#fetch = fetchFunc;
	}

	get #request() {
		return this.#fetch ?? fetch;
	}

	/** The albums whose title or artist match the words (all of them for an empty query). */
	async search(query: string): Promise<Album[]> {
		const res = await api.request(
			this.#request,
			'GET',
			`albums/search?q=${encodeURIComponent(query.trim())}`
		);
		if (!res.ok) throw new AlbumError(res.status);
		return res.json();
	}

	/** The album with its tracks in order, or null when there is none. */
	async get(id: string): Promise<Album | null> {
		const res = await api.request(this.#request, 'GET', `albums/${encodeURIComponent(id)}`);
		if (res.status === 404) return null;
		if (!res.ok) throw new AlbumError(res.status);
		return res.json();
	}

	async create(input: AlbumInput): Promise<Album> {
		const res = await api.request(this.#request, 'POST', 'albums', {
			title: input.title.trim(),
			artist: input.artist.trim(),
			releaseDate: input.releaseDate || null
		});
		if (!res.ok) throw new AlbumError(res.status);
		return res.json();
	}

	/** The albums a tune appears on; empty when the list cannot be had. */
	async ofTune(tuneId: string): Promise<Album[]> {
		const res = await api.request(
			this.#request,
			'GET',
			`tunes/${encodeURIComponent(tuneId)}/albums`
		);
		return res.ok ? res.json() : [];
	}
}

/** The albums the Albums page shows, kept fresh by live events. */
export class AlbumsState {
	albums = $state.raw<Album[]>([]);
	status = $state<'loading' | 'ready' | 'error'>('loading');
	query = '';

	readonly #service: AlbumService;
	readonly #live: Pick<LiveState, 'on' | 'onReconnect'>;
	#loads = 0;

	constructor(
		service = new AlbumService(),
		live: Pick<LiveState, 'on' | 'onReconnect'> = liveState
	) {
		this.#service = service;
		this.#live = live;
	}

	/** Fetches the albums matching the query, which is kept for the refreshes; the latest fetch wins. */
	async load(query = this.query) {
		this.query = query;
		const mine = ++this.#loads;
		try {
			const found = await this.#service.search(query);
			if (mine !== this.#loads) return;
			this.albums = found;
			this.status = 'ready';
		} catch {
			if (mine === this.#loads) this.status = 'error';
		}
	}

	/** Refetches when an album changes, and when events may have been missed. Returns the function that stops it. */
	follow(): () => void {
		return refreshOnLive(this.#live, ['album.changed'], () => void this.load());
	}
}

/** The browser's albums list. */
export const albums = new AlbumsState();
