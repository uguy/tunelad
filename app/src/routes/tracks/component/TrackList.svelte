<script lang="ts">
	import type { Attachment } from 'svelte/attachments';
	import Icon from '#lib/Icon.svelte';
	import FavouriteButton from '#lib/FavouriteButton.svelte';
	import AddToPlaylist from '#lib/AddToPlaylist.svelte';
	import EmptyState from '#lib/ui/EmptyState.svelte';
	import Waveform from '#lib/Waveform.svelte';
	import { artworkGradient } from '#lib/artwork.js';
	import { canChange } from '#lib/permissions.js';
	import { player } from '#lib/player.svelte.js';
	import { session } from '#lib/session.svelte.js';
	import { live } from '#lib/live.svelte.js';
	import { formatOffset } from '#lib/offset.js';
	import IdentificationBadge from '../IdentificationBadge.svelte';
	import PerformanceFields from '../PerformanceFields.svelte';
	import { checkPerformance, toInput, type PerformanceForm } from '../Performances.js';
	import TrackService, {
		CONVERTED_FORMATS,
		type Audio,
		type Track,
		type TrackEdit,
		type Waveform as TrackWaveform
	} from '../Tracks.js';

	interface Props {
		/** The tracks to show; playing one of them makes this list the player's queue. */
		tracks?: Track[];
		/** A tag of a track was clicked. */
		ontagclick?: (tag: string) => void;
		/** A track was edited: `track` is the track as the server now has it. */
		onupdated?: (track: Track) => void;
		/** A track was deleted. */
		ondeleted?: (trackId: string) => void;
	}

	let { tracks = [], ontagclick, onupdated, ondeleted }: Props = $props();

	const trackService = new TrackService();

	/** The waveforms fetched so far, by track id. */
	let waveforms = $state<Record<string, TrackWaveform>>({});
	/** Tracks whose waveform has been asked for, so each is fetched once. Bookkeeping only: nothing renders it. */
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- deliberately not reactive
	const requested = new Set<string>();
	/** The latest fetch of each waveform: an answer to an earlier one must not replace a later answer. */
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- deliberately not reactive
	const latest = new Map<string, number>();
	let alive = true;
	$effect(() => () => {
		alive = false;
	});

	// A waveform is computed some time after the upload, and the server says when: no polling. Waveforms that were
	// never asked for are left alone, as their row fetches them when it comes into view.
	$effect(() => {
		const stopWaveforms = live.on('waveform.computed', ({ id }) => {
			if (id && requested.has(id)) loadWaveform(id);
		});
		// Events may have been missed while the connection was down
		const stopReconnect = live.onReconnect(() => requested.forEach((id) => loadWaveform(id)));
		return () => {
			stopWaveforms();
			stopReconnect();
		};
	});

	/** The state of the audio of the tracks the server converts, by track id: known once fetched. */
	let audios = $state<Record<string, Audio['status']>>({});
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- deliberately not reactive
	const audioRequested = new Set<string>();

	function loadAudio(trackId: string) {
		trackService.findAudio(trackId).then((audio) => {
			if (alive && audio) audios[trackId] = audio.status;
		});
	}

	// Only tracks in a converted format ask: the others play as they are. The server says when the conversion ends,
	// so there is no polling.
	$effect(() => {
		for (const track of tracks) {
			if (CONVERTED_FORMATS.includes(track.format) && !audioRequested.has(track.id)) {
				audioRequested.add(track.id);
				loadAudio(track.id);
			}
		}
	});
	$effect(() => {
		const stopPrepared = live.on('audio.prepared', ({ id }) => {
			if (id && audioRequested.has(id)) loadAudio(id);
		});
		const stopReconnect = live.onReconnect(() => audioRequested.forEach(loadAudio));
		return () => {
			stopPrepared();
			stopReconnect();
		};
	});

	/** Whether the track can be played now: a converted format waits for the server to say so. */
	function playable(track: Track): boolean {
		return !CONVERTED_FORMATS.includes(track.format) || audios[track.id] === 'READY';
	}

	// Identification proposes performances some time after the upload; the track is indexed again when it does. Details
	// that were never opened are left alone.
	$effect(() => {
		const refreshDetail = (id?: string) => {
			if (id && details[id]) loadDetail(id);
		};
		const stopIndexed = live.on('track.indexed', ({ id }) => refreshDetail(id));
		const stopReconnect = live.onReconnect(() => Object.keys(details).forEach(refreshDetail));
		return () => {
			stopIndexed();
			stopReconnect();
		};
	});

	function loadWaveform(trackId: string) {
		const mine = (latest.get(trackId) ?? 0) + 1;
		latest.set(trackId, mine);
		trackService.findWaveform(trackId).then((waveform) => {
			if (alive && waveform && latest.get(trackId) === mine) waveforms[trackId] = waveform;
		});
	}

	/** Fetches the track's waveform once its row comes near the viewport, so a long list does not fetch them all. */
	function waveformWhenVisible(trackId: string): Attachment {
		return (element) => {
			if (requested.has(trackId)) return;
			const observer = new IntersectionObserver(
				(entries) => {
					if (!entries.some((entry) => entry.isIntersecting) || requested.has(trackId)) return;
					requested.add(trackId);
					observer.disconnect();
					loadWaveform(trackId);
				},
				{ rootMargin: '200px' }
			);
			observer.observe(element);
			return () => observer.disconnect();
		};
	}

	function peaksOf(trackId: string): number[] | null {
		const waveform = waveforms[trackId];
		return waveform?.status === 'READY' ? waveform.peaks : null;
	}

	/** Plays the track, or pauses and resumes it when loaded; `fraction` starts or seeks it at that share. */
	function play(index: number, fraction?: number) {
		if (!playable(tracks[index])) return;
		player.playFrom(tracks, index, fraction);
	}

	/** Track ids whose description is shown. */
	let expanded = $state<Record<string, boolean>>({});

	/** The tracks as `GET /api/tracks/{id}` gives them, with their set and performances, fetched when needed. */
	let details = $state<Record<string, Track>>({});

	async function loadDetail(trackId: string): Promise<Track | null> {
		const track = await trackService.findOne(trackId);
		if (track && alive) details[trackId] = track;
		return track;
	}

	function toggle(trackId: string) {
		expanded[trackId] = !expanded[trackId];
		if (expanded[trackId] && !details[trackId]) loadDetail(trackId);
	}

	/** The id of the track being edited, and the form's values; one track at a time. */
	let editing = $state<string | null>(null);
	let form = $state<TrackEdit>({ title: '', description: '', artist: '', album: '' });
	/** The performance fields of the track being edited, one per tune of its set. */
	let performanceForms = $state<PerformanceForm[]>([]);
	let saving = $state(false);
	/** Tracks whose identification was just asked for again, by id. */
	let identifying = $state<Record<string, boolean>>({});
	/** Error message per track id, for the last refused change. */
	let errors = $state<Record<string, string>>({});

	function formOf(track: Track): PerformanceForm[] {
		return (track.performances ?? []).map((performance) => ({
			start: performance.startOffsetSeconds > 0 ? formatOffset(performance.startOffsetSeconds) : '',
			key: performance.key ?? '',
			notes: performance.notes ?? '',
			abc: performance.abc ?? '',
			identification: performance.identification
		}));
	}

	async function startEdit(track: Track) {
		editing = track.id;
		performanceForms = [];
		form = {
			title: track.title ?? '',
			description: track.description ?? '',
			artist: track.artist ?? '',
			album: track.album ?? ''
		};
		errors[track.id] = '';
		// The list comes from search, which has no performances: the track is fetched whole
		const whole = details[track.id] ?? (await loadDetail(track.id));
		if (editing === track.id && whole) performanceForms = formOf(whole);
	}

	function cancelEdit() {
		editing = null;
	}

	async function saveEdit(track: Track) {
		const invalid = performanceForms.map(checkPerformance).find(Boolean);
		if (invalid) {
			errors[track.id] = invalid;
			return;
		}
		saving = true;
		try {
			const updated = await trackService.update(track.id, {
				title: form.title,
				description: form.description,
				// An empty field clears the artist or album
				artist: form.artist || null,
				album: form.album || null,
				performances: performanceForms.length ? performanceForms.map(toInput) : undefined
			});
			details[track.id] = updated;
			editing = null;
			onupdated?.(updated);
		} catch (e) {
			errors[track.id] = (e as Error).message;
		} finally {
			saving = false;
		}
	}

	async function identifyAgain(track: Track) {
		errors[track.id] = '';
		try {
			await trackService.identify(track.id);
			identifying[track.id] = true;
		} catch (e) {
			errors[track.id] = (e as Error).message;
		}
	}

	async function deleteTrack(track: Track) {
		if (!window.confirm(`Delete "${track.title}"? This cannot be undone.`)) return;
		try {
			await trackService.delete(track.id);
			ondeleted?.(track.id);
		} catch (e) {
			errors[track.id] = (e as Error).message;
		}
	}

	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
</script>

<div id="track-list" class="flex w-full flex-col gap-4">
	{#if tracks.length === 0}
		<EmptyState title="No tracks found">Try other words, or upload a track of your own.</EmptyState>
	{:else}
		{#each tracks as track, index (track.id)}
			{@const playing = player.isPlaying(track.id)}
			{@const loaded = player.isLoaded(track.id)}
			<article
				data-testid="track-{track.id}"
				class="card flex gap-4 border-l-4 p-4 transition-shadow hover:shadow-md {loaded
					? 'border-accent'
					: 'border-transparent'}"
				{@attach waveformWhenVisible(track.id)}
			>
				<div
					class="hidden h-36 w-36 shrink-0 rounded-sm sm:block"
					style:background-image={artworkGradient(track.id)}
					aria-hidden="true"
				></div>

				<div class="flex min-w-0 flex-1 flex-col">
					<div class="flex items-start gap-3">
						<button
							id="play-track-{track.id}-btn"
							type="button"
							aria-label="{playing ? 'pause' : 'play'} track {track.title} button"
							class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent shadow-sm transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50"
							disabled={!playable(track)}
							onclick={() => play(index)}
						>
							<Icon name={playing ? 'pause' : 'play'} class="h-6 w-6" />
						</button>

						<div class="min-w-0 flex-1 leading-tight">
							{#if track.owner}
								<p class="truncate text-sm text-ink-muted" data-testid="track-owner">
									{track.owner.displayName}
								</p>
							{/if}
							<h2
								class="truncate font-medium {loaded ? 'text-accent-ink' : ''}"
								data-testid="track-title"
							>
								{track.title}
							</h2>
							{#if audios[track.id] === 'PENDING'}
								<p role="status" class="text-xs text-ink-muted" data-testid="audio-status">
									Preparing audio…
								</p>
							{:else if audios[track.id] === 'FAILED'}
								<p role="status" class="text-xs text-danger" data-testid="audio-status">
									Audio could not be prepared.
								</p>
							{/if}
						</div>

						<FavouriteButton trackId={track.id} title={track.title} />
						<AddToPlaylist trackId={track.id} title={track.title} />

						{#if canChange(session.account, track)}
							<div class="flex shrink-0 gap-2">
								<button
									type="button"
									aria-label="edit track {track.title}"
									class="rounded-full border border-line px-3 py-1 text-xs font-medium hover:border-ink-muted"
									onclick={() => startEdit(track)}
								>
									Edit
								</button>
								<button
									type="button"
									aria-label="delete track {track.title}"
									class="rounded-full border border-danger-line px-3 py-1 text-xs font-medium text-danger hover:bg-danger-soft"
									onclick={() => deleteTrack(track)}
								>
									Delete
								</button>
							</div>
						{/if}
					</div>

					<div class="mt-3">
						<Waveform
							peaks={peaksOf(track.id)}
							progress={player.progressOf(track.id)}
							label={track.title}
							onseek={(fraction) => play(index, fraction)}
						/>
					</div>

					<div class="mt-2 flex flex-wrap items-center gap-2">
						{#each track.tags ?? [] as tag (tag)}
							<button
								type="button"
								class="rounded-full bg-canvas px-2.5 py-0.5 text-xs font-medium text-ink-muted hover:bg-accent-soft hover:text-accent-ink"
								onclick={() => ontagclick?.(tag)}
							>
								#{tag}
							</button>
						{/each}
						<button
							type="button"
							class="ml-auto inline-flex items-center gap-1 text-xs font-medium text-ink-muted hover:text-ink"
							aria-label="show track {track.title} detail button"
							aria-expanded={!!expanded[track.id]}
							onclick={() => toggle(track.id)}
						>
							{expanded[track.id] ? 'Less' : 'More'}
							<Icon
								name="chevron"
								class="h-4 w-4 transition-transform {expanded[track.id] ? 'rotate-180' : ''}"
							/>
						</button>
					</div>

					{#if expanded[track.id]}
						<div class="details mt-3 border-t border-line pt-3 text-sm leading-normal">
							{#if track.artist}
								<p><span class="text-ink-muted">Artist:</span> {track.artist}</p>
							{/if}
							{#if track.album}
								<p><span class="text-ink-muted">Album:</span> {track.album}</p>
							{/if}
							<p class="mt-1 whitespace-pre-line">{track.description}</p>
							{#if details[track.id]?.performances?.length}
								<h3 class="mt-3 font-medium">Tunes</h3>
								<ol class="mt-1 space-y-1" aria-label="tunes of {track.title}">
									{#each details[track.id].performances ?? [] as performance (performance.position)}
										<li>
											<span class="text-ink-muted tabular-nums">{performance.position}.</span>
											{performance.tuneName}
											<span class="text-ink-muted capitalize">{performance.danceType}</span>
											<span class="text-ink-muted">
												· starts at <span class="tabular-nums">
													{formatOffset(performance.startOffsetSeconds)}
												</span>{#if performance.key}
													· key {performance.key}{/if}
											</span>
											<IdentificationBadge state={performance.identification} />
											{#if performance.notes}
												<p class="ml-5 whitespace-pre-line text-ink-muted">{performance.notes}</p>
											{/if}
										</li>
									{/each}
								</ol>
								{#if canChange(session.account, track)}
									<div class="mt-2 flex flex-wrap items-center gap-2">
										<button
											type="button"
											class="rounded-full border border-line px-3 py-1 text-xs font-medium hover:bg-canvas"
											aria-label="identify the tunes of {track.title} again"
											onclick={() => identifyAgain(track)}
										>
											Identify again
										</button>
										{#if identifying[track.id]}
											<p role="status" class="text-xs text-ink-muted">
												Listening to the track. Proposed starts appear here when it is done.
											</p>
										{/if}
									</div>
								{/if}
							{/if}
						</div>
					{/if}

					{#if editing === track.id}
						<form
							class="mt-3 space-y-3 border-t border-line pt-3"
							aria-label="edit track {track.title}"
							onsubmit={(event) => {
								event.preventDefault();
								saveEdit(track);
							}}
						>
							<label class="block text-sm font-medium">
								Title <input class={inputClass} required bind:value={form.title} />
							</label>
							<label class="block text-sm font-medium">
								Description <textarea class={inputClass} required bind:value={form.description}
								></textarea>
							</label>
							<div class="grid gap-3 sm:grid-cols-2">
								<label class="block text-sm font-medium">
									Artist <input class={inputClass} bind:value={form.artist} />
								</label>
								<label class="block text-sm font-medium">
									Album <input class={inputClass} bind:value={form.album} />
								</label>
							</div>
							{#if performanceForms.length}
								<PerformanceFields
									names={(details[track.id]?.performances ?? []).map((p) => p.tuneName)}
									bind:values={performanceForms}
								/>
							{/if}
							<div class="flex gap-2">
								<button type="submit" class="btn-accent" disabled={saving}>Save</button>
								<button type="button" class="btn-quiet" onclick={cancelEdit}>Cancel</button>
							</div>
						</form>
					{/if}

					{#if errors[track.id]}
						<p role="alert" class="mt-2 text-sm text-danger">{errors[track.id]}</p>
					{/if}
				</div>
			</article>
		{/each}
	{/if}
</div>

<style>
	.details {
		animation: reveal 0.25s ease;
	}

	@keyframes reveal {
		from {
			opacity: 0;
			transform: translateY(-0.25rem);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}
</style>
