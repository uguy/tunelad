/** What the global player should load: a track, and where to start it (in seconds). */
export interface MediaSource {
	trackId: string;
	start: number;
}

class PlayerState {
	/** The track the global player has loaded, if any. Raw: a source is replaced, never mutated. */
	source = $state.raw<MediaSource | null>(null);

	/** Loads the track in the global player, starting at `start` seconds. */
	play(trackId: string, start = 0) {
		this.source = { trackId, start };
	}
}

/** The state of the global player, shared by the pages and the layout that renders it. */
export const player = new PlayerState();
