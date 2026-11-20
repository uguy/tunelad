import * as api from '#lib/api.js';

/** A track as the API returns it. */
export interface Track {
	id: string;
	title: string;
	description: string;
	artist?: string | null;
	album?: string | null;
	format: string;
	owner?: { id: string; displayName: string } | null;
	tags?: string[];
}

/** A track's metadata as an edit replaces it: an omitted artist or album is cleared. */
export interface TrackEdit {
	title: string;
	description: string;
	artist?: string | null;
	album?: string | null;
}

/** The server refused a change to a track; `status` is its HTTP status. */
export class TrackChangeError extends Error {
	constructor(readonly status: number) {
		super(
			status === 403
				? 'You may not change this track.'
				: status === 404
					? 'This track no longer exists.'
					: status === 400
						? 'A track needs a title and a description.'
						: `The server refused the change (${status}).`
		);
	}
}

export default class TrackService {
	#fetchFunc: typeof fetch;

	/** `fetchFunc` is the fetch to use: SvelteKit's in a load function, the global one by default. */
	constructor(fetchFunc: typeof fetch = fetch) {
		this.#fetchFunc = fetchFunc;
	}

	/** The tracks matching the query (all of them for an empty one); none if the search fails. */
	async findAll(query: string): Promise<Track[]> {
		return await api.get(this.#fetchFunc, `tracks/search?q=${encodeURIComponent(query)}`);
	}

	/** Replaces the track's metadata and returns the updated track; throws a {@link TrackChangeError} if refused. */
	async update(id: string, edit: TrackEdit): Promise<Track> {
		const res = await api.request(this.#fetchFunc, 'PUT', `tracks/${encodeURIComponent(id)}`, edit);
		if (!res.ok) throw new TrackChangeError(res.status);
		return await res.json();
	}

	/** Deletes the track; throws a {@link TrackChangeError} if refused. */
	async delete(id: string) {
		const res = await api.request(this.#fetchFunc, 'DELETE', `tracks/${encodeURIComponent(id)}`);
		if (!res.ok) throw new TrackChangeError(res.status);
	}
}
