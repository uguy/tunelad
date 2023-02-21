import TrackService from './Tracks.js';

/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
	const trackService = new TrackService(fetch);
	return {
		tracks: await trackService.findAll('')
	};
}
