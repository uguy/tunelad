import * as api from '#lib/api.js';
import type { TuneSet } from '../sets/Sets.js';
import type { PerformanceInput } from './Performances.js';

/** Where a performance stands in identification of the audio. */
export type IdentificationState = 'pending' | 'identified' | 'nothing_found' | 'corrected';

/** A track as the API returns it. */
export interface Track {
	id: string;
	title: string;
	description: string;
	artist?: string | null;
	album?: string | null;
	format: string;
	owner?: { id: string; displayName: string } | null;
	tags?: string[];
	/** The set the track records: absent from search results and from tracks stored before sets existed. */
	set?: TuneSet | null;
	/** One performance per tune of the set, in order: absent from search results. */
	performances?: Performance[];
}

/** One tune as played in a track. */
export interface Performance {
	position: number;
	tuneId: string;
	tuneName: string;
	danceType: string;
	startOffsetSeconds: number;
	key?: string | null;
	notes?: string | null;
	abc?: string | null;
	identification?: IdentificationState;
}

/** The waveform of a track's audio: its peaks, from 0 to 1, once `READY`; none while `PENDING` or when `FAILED`. */
export interface Waveform {
	status: 'PENDING' | 'READY' | 'FAILED';
	peaks: number[];
}

/** A track's metadata as an edit replaces it: an omitted artist or album is cleared. */
export interface TrackEdit {
	title: string;
	description: string;
	artist?: string | null;
	album?: string | null;
	/** One entry per performance, in order; omitted leaves them unchanged. */
	performances?: PerformanceInput[];
}

/** The server refused a change to a track; `status` is its HTTP status. */
export class TrackChangeError extends Error {
	constructor(readonly status: number) {
		super(
			status === 403
				? 'You may not change this track.'
				: status === 404
					? 'This track no longer exists.'
					: status === 400
						? 'A track needs a title and a description, and performances that fit.'
						: `The server refused the change (${status}).`
		);
	}
}

/** A new track as the upload form sends it: an empty artist or album is left out. */
export interface TrackUpload {
	title: string;
	description: string;
	artist?: string | null;
	album?: string | null;
	/** The id of the set the track records. */
	setId: string;
	/** One entry per tune of the set, in order. */
	performances?: PerformanceInput[];
	file: File;
}

/** File name extensions the server accepts, as `TrackFormat` on the server lists them. */
export const SUPPORTED_EXTENSIONS = ['mp3', 'm4a', 'ogg', 'flac', 'wav'] as const;

/** The largest audio file the server accepts, as `spring.webflux.multipart.max-disk-usage-per-part` sets it. */
export const MAX_FILE_SIZE = 100 * 1024 * 1024;

/** The `accept` attribute of the file picker: the supported extensions and their usual content types. */
export const ACCEPTED_FILES = [
	...SUPPORTED_EXTENSIONS.map((extension) => `.${extension}`),
	'audio/mpeg',
	'audio/mp4',
	'audio/ogg',
	'audio/flac',
	'audio/wav'
].join(',');

/**
 * What to tell the user when an upload fails with that HTTP status; 0 stands for a connection failure. The checks
 * made before sending use the 413 and 415 messages, so a refusal reads the same wherever it comes from.
 */
export function uploadErrorMessage(status: number): string {
	switch (status) {
		case 0:
			return 'The connection was lost. Your file is still selected: try again.';
		case 400:
			return 'A track needs a title, a description, a set and an audio file, and valid performances.';
		case 401:
			return 'Your session has expired. Log in again, then upload the track again.';
		case 403:
			return 'You may not upload this track. Reload the page and try again.';
		case 413:
			return 'This file is larger than the 100 MB limit.';
		case 415:
			return "This file type isn't supported: choose an mp3, m4a, ogg, flac or wav file.";
		default:
			return `The server could not store the track (${status}). Try again.`;
	}
}

/** Why the file can't be uploaded, in the words of the server's refusal; undefined when it can. */
export function checkFile(file: Pick<File, 'name' | 'size'>): string | undefined {
	const dot = file.name.lastIndexOf('.');
	const extension = dot < 0 ? '' : file.name.substring(dot + 1).toLowerCase();
	if (!(SUPPORTED_EXTENSIONS as readonly string[]).includes(extension))
		return uploadErrorMessage(415);
	if (file.size > MAX_FILE_SIZE) return uploadErrorMessage(413);
	return undefined;
}

/** The share of the file sent, from 0 to 1, from an upload progress event's values; 0 when the total is unknown. */
export function progressFraction(loaded: number, total: number, lengthComputable = true): number {
	if (!lengthComputable || total <= 0) return 0;
	return Math.min(1, Math.max(0, loaded / total));
}

/** The server refused an upload, or the connection failed (`status` 0); `message` says so to the user. */
export class TrackUploadError extends Error {
	constructor(readonly status: number) {
		super(uploadErrorMessage(status));
	}
}

export interface UploadOptions {
	/** Called as the file is sent, with the share sent so far, from 0 to 1. */
	onProgress?: (fraction: number) => void;
	/** Aborting it cancels the upload, which then rejects with an `AbortError`. */
	signal?: AbortSignal;
}

export default class TrackService {
	#fetchFunc: typeof fetch;

	/** `fetchFunc` is the fetch to use: SvelteKit's in a load function, the global one by default. */
	constructor(fetchFunc: typeof fetch = fetch) {
		this.#fetchFunc = fetchFunc;
	}

	/** The tracks matching the query (all of them for an empty one); none if the search fails. */
	async findAll(query: string): Promise<Track[]> {
		return await api.get(this.#fetchFunc, `tracks/search?q=${encodeURIComponent(query)}`);
	}

	/** The tracks matching the query, or null when the search fails, so that a page can keep what it shows. */
	async tryFindAll(query: string): Promise<Track[] | null> {
		try {
			const res = await api.request(
				this.#fetchFunc,
				'GET',
				`tracks/search?q=${encodeURIComponent(query)}`
			);
			return res.ok ? await res.json() : null;
		} catch {
			return null;
		}
	}

	/** The track with that id, or null when there is none or it cannot be fetched. */
	async findOne(id: string): Promise<Track | null> {
		try {
			const res = await api.request(this.#fetchFunc, 'GET', `tracks/${encodeURIComponent(id)}`);
			return res.ok ? await res.json() : null;
		} catch {
			return null;
		}
	}

	/** The waveform of the track, or null when the track does not exist or the waveform cannot be fetched. */
	async findWaveform(id: string): Promise<Waveform | null> {
		try {
			const res = await api.request(
				this.#fetchFunc,
				'GET',
				`tracks/${encodeURIComponent(id)}/waveform`
			);
			return res.ok ? await res.json() : null;
		} catch {
			return null;
		}
	}

	/** Replaces the track's metadata and returns the updated track; throws a {@link TrackChangeError} if refused. */
	async update(id: string, edit: TrackEdit): Promise<Track> {
		const res = await api.request(this.#fetchFunc, 'PUT', `tracks/${encodeURIComponent(id)}`, edit);
		if (!res.ok) throw new TrackChangeError(res.status);
		return await res.json();
	}

	/** Asks the server to hear the track's audio again; throws a {@link TrackChangeError} if refused. */
	async identify(id: string): Promise<void> {
		const res = await api.request(
			this.#fetchFunc,
			'POST',
			`tracks/${encodeURIComponent(id)}/identification`
		);
		if (!res.ok) throw new TrackChangeError(res.status);
	}

	/**
	 * Uploads a new track and returns it as the server stored it. Uses `XMLHttpRequest` rather than fetch, which can't
	 * report upload progress. Rejects with a {@link TrackUploadError} when refused or when the connection fails, and
	 * with a `DOMException` named `AbortError` when cancelled through `options.signal`.
	 */
	upload(upload: TrackUpload, { onProgress, signal }: UploadOptions = {}): Promise<Track> {
		return new Promise((resolve, reject) => {
			const cancelled = () => new DOMException('The upload was cancelled.', 'AbortError');
			if (signal?.aborted) {
				reject(cancelled());
				return;
			}
			const xhr = new XMLHttpRequest();
			xhr.open('POST', '/api/tracks');
			xhr.responseType = 'json';
			const token = api.csrfToken();
			if (token) xhr.setRequestHeader('X-XSRF-TOKEN', token);
			xhr.upload.onprogress = (event) =>
				onProgress?.(progressFraction(event.loaded, event.total, event.lengthComputable));
			xhr.onload = () => {
				if (xhr.status >= 200 && xhr.status < 300) resolve(xhr.response as Track);
				else reject(new TrackUploadError(xhr.status));
			};
			xhr.onerror = () => reject(new TrackUploadError(0));
			xhr.onabort = () => reject(cancelled());
			signal?.addEventListener('abort', () => xhr.abort(), { once: true });

			const form = new FormData();
			form.append('setId', upload.setId);
			if (upload.performances?.length)
				form.append('performances', JSON.stringify(upload.performances));
			form.append('title', upload.title);
			form.append('description', upload.description);
			if (upload.artist) form.append('artist', upload.artist);
			if (upload.album) form.append('album', upload.album);
			form.append('file', upload.file);
			xhr.send(form);
		});
	}

	/** Deletes the track; throws a {@link TrackChangeError} if refused. */
	async delete(id: string) {
		const res = await api.request(this.#fetchFunc, 'DELETE', `tracks/${encodeURIComponent(id)}`);
		if (!res.ok) throw new TrackChangeError(res.status);
	}
}
