import { writable } from 'svelte/store';

/** What the global player should load: a track, and where to start it (in seconds). */
export interface MediaSource {
	trackId: string;
	start: number;
}

export default writable<MediaSource | null>(null);
