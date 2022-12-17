import * as api from '$lib/api.js';

/** @type {import('./$types').PageLoad} */
export async function load({ params }) {
	return {
		tracks: await api.get('api/search/tracks?q=')
	};
}
