import * as api from '$lib/api.js';

export default class TrackService {
	#fetchFunc: typeof fetch;

	constructor(fetchFunc: typeof fetch | undefined) {
		this.#fetchFunc = fetchFunc ? fetchFunc : fetch;
	}

	async findAll(request: string) {
		return await api.get(this.#fetchFunc, 'tracks/search?q=' + request);
	}
}
