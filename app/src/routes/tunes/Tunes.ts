import { request } from '#lib/api.js';
import type { ArtistKind } from '#lib/artists.js';
import type { IdentificationState } from '../tracks/Tracks.js';

/** A tune as the API returns it. */
export interface Tune {
	id: string;
	name: string;
	aliases: string[];
	author?: string | null;
	danceType: string;
	key?: string | null;
	sourceId?: number | null;
	abc?: string | null;
}

/** One recording of a tune, as the API lists it on the tune. */
export interface TunePerformance {
	trackId: string;
	trackTitle: string;
	artist?: string | null;
	artistId?: string | null;
	artistKind?: ArtistKind | null;
	position: number;
	startOffsetSeconds: number;
	key?: string | null;
	notes?: string | null;
	abc?: string | null;
	identification?: IdentificationState;
}

/** A new tune as the form sends it: an empty optional field is left out. */
export interface TuneInput {
	name: string;
	danceType: string;
	aliases: string[];
	author?: string;
	key?: string;
	abc?: string;
}

/** The server refused a request about tunes; `status` is its HTTP status. */
export class TuneError extends Error {
	constructor(readonly status: number) {
		super(tuneErrorMessage(status));
	}
}

export function tuneErrorMessage(status: number): string {
	switch (status) {
		case 400:
			return 'A tune needs a name and a dance type.';
		case 401:
			return 'Log in to add tunes.';
		case 404:
			return 'thesession.org does not know this tune.';
		case 502:
			return 'thesession.org could not be reached. Try again in a moment.';
		default:
			return `The server refused the request (${status}).`;
	}
}

/** Splits the aliases field, one alias per line or separated by semicolons, dropping blanks. */
export function parseAliases(text: string): string[] {
	return text
		.split(/[\n;]/)
		.map((alias) => alias.trim())
		.filter((alias) => alias !== '');
}

/** The thesession.org tune id in a number or a tune URL such as https://thesession.org/tunes/27; null if neither. */
export function parseSourceId(text: string): number | null {
	const match = /^\s*(?:https?:\/\/thesession\.org\/tunes\/)?(\d+)\b/.exec(text);
	return match ? Number(match[1]) : null;
}

/** The tunes, the dance types, and adding tunes. `fetch` is the page's own during load. */
export default class TuneService {
	constructor(
		private readonly fetch: typeof globalThis.fetch = globalThis.fetch.bind(globalThis)
	) {}

	async danceTypes(): Promise<string[]> {
		const res = await request(this.fetch, 'GET', 'dance-types');
		return res.ok ? res.json() : [];
	}

	async findAll(danceType = '', text = ''): Promise<Tune[]> {
		const params = new URLSearchParams();
		if (danceType) params.set('danceType', danceType);
		if (text.trim()) params.set('q', text.trim());
		const res = await request(this.fetch, 'GET', `tunes?${params}`);
		if (!res.ok) throw new TuneError(res.status);
		return res.json();
	}

	/** The tune, or null when there is no such tune. */
	async findOne(id: string): Promise<Tune | null> {
		const res = await request(this.fetch, 'GET', `tunes/${encodeURIComponent(id)}`);
		if (res.status === 404) return null;
		if (!res.ok) throw new TuneError(res.status);
		return res.json();
	}

	/** Every Performance of the tune across tracks; empty when the list cannot be had. */
	async performances(id: string): Promise<TunePerformance[]> {
		const res = await request(this.fetch, 'GET', `tunes/${encodeURIComponent(id)}/performances`);
		return res.ok ? res.json() : [];
	}

	async create(input: TuneInput): Promise<Tune> {
		return this.#post('tunes', input);
	}

	/** Imports the thesession.org tune; an id imported before answers with the existing tune. */
	async importFrom(sourceId: number): Promise<Tune> {
		return this.#post('tunes/import', { id: sourceId });
	}

	async #post(path: string, body: unknown): Promise<Tune> {
		const res = await request(this.fetch, 'POST', path, body);
		if (!res.ok) throw new TuneError(res.status);
		return res.json();
	}
}
