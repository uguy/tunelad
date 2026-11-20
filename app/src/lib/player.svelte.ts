/** What the global player should load: a track, and where to start it (in seconds). */
export interface MediaSource {
	trackId: string;
	start: number;
}

/** What the player needs to know of a track to show it. */
export interface PlayerTrack {
	id: string;
	title: string;
	owner?: { displayName: string } | null;
}

/** Previous restarts the current track rather than going back, once it has played this long (seconds). */
export const RESTART_THRESHOLD = 3;

const STORAGE_KEY = 'tunelad.player';

/**
 * The state of the app's one audio player, shared by the pages, which start tracks, and the player bar, which owns
 * the `<audio>` element and binds its time, duration, paused, volume and muted state to the fields here.
 */
export class PlayerState {
	/** The track the player has loaded, if any. Raw: a source is replaced, never mutated. */
	source = $state.raw<MediaSource | null>(null);
	/** The loaded track, when known: a track opened by id (a link) is only known once it has been fetched. */
	track = $state.raw<PlayerTrack | null>(null);
	/** The tracks next and previous go through: the list a track was started from, empty for a link. */
	queue = $state.raw<PlayerTrack[]>([]);
	/** The position of the loaded track in the queue, or -1. */
	index = $state(-1);

	paused = $state(true);
	currentTime = $state(0);
	duration = $state(0);
	volume = $state(1);
	muted = $state(false);

	/** Where to seek, as a share of the duration, once the loaded track's duration is known. */
	#pendingFraction: number | null = null;
	readonly #storage: Pick<Storage, 'getItem' | 'setItem'> | undefined;

	/** `storage` keeps the volume between visits: the browser's local storage by default. */
	constructor(
		storage: Pick<Storage, 'getItem' | 'setItem'> | undefined = typeof window === 'undefined'
			? undefined
			: window.localStorage
	) {
		this.#storage = storage;
		this.#restoreVolume();
	}

	get hasNext() {
		return this.index >= 0 && this.index < this.queue.length - 1;
	}

	get hasPrevious() {
		return this.source !== null;
	}

	/** Whether that track is the loaded one. */
	isLoaded(trackId: string) {
		return this.source?.trackId === trackId;
	}

	/** Whether that track is loaded and playing. */
	isPlaying(trackId: string) {
		return this.isLoaded(trackId) && !this.paused;
	}

	/** How much of that track has played, from 0 to 1; 0 when it is not loaded. */
	progressOf(trackId: string) {
		return this.isLoaded(trackId) && this.duration > 0
			? Math.min(1, this.currentTime / this.duration)
			: 0;
	}

	/** Loads a track by id, as a link does: no queue, starting at `start` seconds. */
	play(trackId: string, start = 0, track: PlayerTrack | null = null) {
		this.queue = [];
		this.index = -1;
		this.#load(trackId, start, track);
	}

	/**
	 * Plays the track at `index` of `queue`, which becomes the player's queue. When it is the loaded track, toggles
	 * play and pause instead, unless `fraction` asks to seek to a share of its duration.
	 */
	playFrom(queue: PlayerTrack[], index: number, fraction?: number) {
		const track = queue[index];
		if (!track) return;
		this.queue = queue;
		this.index = index;
		if (this.isLoaded(track.id)) {
			if (fraction === undefined) this.paused = !this.paused;
			else {
				this.seekFraction(fraction);
				this.paused = false;
			}
			return;
		}
		this.#load(track.id, 0, track);
		this.#pendingFraction = fraction ?? null;
	}

	/** Plays or pauses the loaded track. */
	toggle() {
		if (this.source) this.paused = !this.paused;
	}

	/** Moves the playback position to `seconds`, within the track. */
	seek(seconds: number) {
		const max = this.duration > 0 ? this.duration : Infinity;
		this.currentTime = Math.max(0, Math.min(seconds, max));
	}

	/** Moves the playback position to a share of the track's duration, from 0 to 1. */
	seekFraction(fraction: number) {
		this.seek(Math.max(0, Math.min(1, fraction)) * this.duration);
	}

	/** Loads the next track of the queue, if any. */
	next() {
		if (!this.hasNext) return;
		this.index += 1;
		const track = this.queue[this.index];
		this.#load(track.id, 0, track);
	}

	/** Restarts the loaded track once it has played a little, otherwise loads the previous track of the queue. */
	previous() {
		if (this.currentTime > RESTART_THRESHOLD || this.index <= 0) {
			this.seek(0);
			return;
		}
		this.index -= 1;
		const track = this.queue[this.index];
		this.#load(track.id, 0, track);
	}

	/** The loaded track reached its end: the next one plays, if any. */
	ended() {
		if (this.hasNext) this.next();
		else this.paused = true;
	}

	/** The loaded track's duration is known: applies a seek asked for before it was. */
	metadataLoaded(duration: number) {
		this.duration = duration;
		if (this.#pendingFraction !== null) {
			this.seekFraction(this.#pendingFraction);
			this.#pendingFraction = null;
		}
	}

	/** Sets the volume, from 0 to 1, unmuting, and remembers it for the next visit. */
	setVolume(volume: number) {
		this.volume = Math.max(0, Math.min(1, volume));
		this.muted = false;
		this.#saveVolume();
	}

	toggleMute() {
		this.muted = !this.muted;
		this.#saveVolume();
	}

	#load(trackId: string, start: number, track: PlayerTrack | null) {
		this.source = { trackId, start };
		this.track = track;
		this.currentTime = start;
		this.duration = 0;
		this.#pendingFraction = null;
		this.paused = false;
	}

	#restoreVolume() {
		try {
			const saved = JSON.parse(this.#storage?.getItem(STORAGE_KEY) ?? 'null');
			if (typeof saved?.volume === 'number') this.volume = Math.max(0, Math.min(1, saved.volume));
			if (typeof saved?.muted === 'boolean') this.muted = saved.muted;
		} catch {
			// Nothing usable saved: keep the defaults
		}
	}

	#saveVolume() {
		try {
			this.#storage?.setItem(
				STORAGE_KEY,
				JSON.stringify({ volume: this.volume, muted: this.muted })
			);
		} catch {
			// Storage full or disabled: the volume is only kept for this visit
		}
	}
}

/** The state of the global player, shared by the pages and the layout that renders it. */
export const player = new PlayerState();
