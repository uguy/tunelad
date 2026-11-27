import { request } from '#lib/api.js';

/** The most tunes a set can have. */
export const MAX_TUNES = 20;

/** A tune of a set. */
export interface SetTune {
	id: string;
	name: string;
	danceType: string;
}

/** A set as the API returns it. */
export interface TuneSet {
	id: string;
	tunes: SetTune[];
}

/** The server refused a request about sets; `status` is its HTTP status. */
export class SetError extends Error {
	constructor(readonly status: number) {
		super(setErrorMessage(status));
	}
}

export function setErrorMessage(status: number): string {
	switch (status) {
		case 400:
			return `A set needs one to ${MAX_TUNES} tunes that exist.`;
		case 401:
			return 'Log in to create sets.';
		default:
			return `The server refused the request (${status}).`;
	}
}

/** The names of a set's tunes, as a title: "Cooley's, The Kesh". */
export function setTitle(set: TuneSet): string {
	return set.tunes.map((tune) => tune.name).join(', ');
}

/** The sets, and creating one. `fetch` is the page's own during load. */
export default class SetService {
	constructor(
		private readonly fetch: typeof globalThis.fetch = globalThis.fetch.bind(globalThis)
	) {}

	async findAll(tuneId = ''): Promise<TuneSet[]> {
		const params = new URLSearchParams();
		if (tuneId) params.set('tuneId', tuneId);
		const res = await request(this.fetch, 'GET', `sets?${params}`);
		if (!res.ok) throw new SetError(res.status);
		return res.json();
	}

	/** The set, or null when there is no such set. */
	async findOne(id: string): Promise<TuneSet | null> {
		const res = await request(this.fetch, 'GET', `sets/${encodeURIComponent(id)}`);
		if (res.status === 404) return null;
		if (!res.ok) throw new SetError(res.status);
		return res.json();
	}

	/** The set of this ordered list of tunes; `created` is false when it existed already. */
	async create(tuneIds: string[]): Promise<{ set: TuneSet; created: boolean }> {
		const res = await request(this.fetch, 'POST', 'sets', { tuneIds });
		if (!res.ok) throw new SetError(res.status);
		return { set: await res.json(), created: res.status === 201 };
	}
}
