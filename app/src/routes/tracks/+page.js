import * as api from '$lib/api.js';

/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
	return {
		tracks: await api.get(fetch, 'search/tracks?q=')
	};
}
