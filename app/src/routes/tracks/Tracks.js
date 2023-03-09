import * as api from '$lib/api.js';

export default class TrackService {
	#fetchFunc;
	constructor(fetchFunc) {
		this.#fetchFunc = fetchFunc ? fetchFunc : fetch;
	}
	async findAll(request) {
		return await api.get(this.#fetchFunc, 'search/tracks?q=' + request);
	}
}
