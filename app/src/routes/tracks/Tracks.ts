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

	constructor(fetchFunc: typeof fetch | undefined) {
		this.#fetchFunc = fetchFunc ? fetchFunc : fetch;
	}

	async findAll(request: string) {
		return await api.get(this.#fetchFunc, 'tracks/search?q=' + request);
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
