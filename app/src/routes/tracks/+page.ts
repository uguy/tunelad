import type { PageLoad } from './$types';
import TrackService from './Tracks.js';

export const load: PageLoad = async ({ fetch }) => {
	const trackService = new TrackService(fetch);
	return {
		tracks: await trackService.findAll('')
	};
};
