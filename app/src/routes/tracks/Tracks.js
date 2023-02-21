import * as api from '$lib/api.js';

export default class TrackService {
	_fetchFunc = undefined;
	constructor(fetchFunc) {
		this._fetchFunc = fetchFunc ? fetchFunc : fetch;
	}
	async findAll(request) {
		return await api.get(this._fetchFunc, 'search/tracks?q=' + request);
	}
}
