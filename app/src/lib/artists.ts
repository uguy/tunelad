import { request } from './api.js';

/** What an Artist is; absent while it is not classified. */
export type ArtistKind = 'musician' | 'band';

/** An Artist named by id. */
export interface ArtistRef {
	id: string;
	name: string;
}

/** An Artist as the API returns it: a band lists its members, a musician its bands. */
export interface Artist extends ArtistRef {
	kind?: ArtistKind | null;
	members: ArtistRef[];
	bands: ArtistRef[];
}

/** The Artist of a Performance, as far as the Performance says. */
export interface PerformanceArtist {
	artistId?: string | null;
	artist?: string | null;
	artistKind?: ArtistKind | null;
}

/** The distinct Artists of the Performances, in order of first appearance; Performances without an Artist are skipped. */
export function distinctArtists(
	performances: PerformanceArtist[]
): (ArtistRef & { kind: ArtistKind | null })[] {
	const seen = new Map<string, ArtistRef & { kind: ArtistKind | null }>();
	for (const p of performances) {
		if (p.artistId && !seen.has(p.artistId)) {
			seen.set(p.artistId, { id: p.artistId, name: p.artist ?? '', kind: p.artistKind ?? null });
		}
	}
	return [...seen.values()];
}

/** Splits the members field, one name per line or separated by semicolons, dropping blanks. */
export function parseMembers(text: string): string[] {
	return text
		.split(/[\n;]/)
		.map((name) => name.trim())
		.filter((name) => name !== '');
}

/** The server refused a request about artists; `status` is its HTTP status. */
export class ArtistError extends Error {
	constructor(readonly status: number) {
		super(artistErrorMessage(status));
	}
}

export function artistErrorMessage(status: number): string {
	switch (status) {
		case 400:
			return 'A band cannot be its own member.';
		case 401:
			return 'Log in to edit artists.';
		case 404:
			return 'This artist no longer exists.';
		case 409:
			return 'A band with members cannot become a musician, and a band cannot be a member of a band.';
		default:
			return `The server refused the request (${status}).`;
	}
}

export default class ArtistService {
	constructor(
		private readonly fetch: typeof globalThis.fetch = globalThis.fetch.bind(globalThis)
	) {}

	async get(id: string): Promise<Artist> {
		return this.#json(await request(this.fetch, 'GET', `artists/${encodeURIComponent(id)}`));
	}

	async setKind(id: string, kind: ArtistKind | null): Promise<Artist> {
		return this.#json(
			await request(this.fetch, 'PUT', `artists/${encodeURIComponent(id)}`, { kind })
		);
	}

	async setMembers(id: string, members: string[]): Promise<Artist> {
		return this.#json(
			await request(this.fetch, 'PUT', `artists/${encodeURIComponent(id)}/members`, { members })
		);
	}

	async #json(res: Response): Promise<Artist> {
		if (!res.ok) throw new ArtistError(res.status);
		return res.json();
	}
}
