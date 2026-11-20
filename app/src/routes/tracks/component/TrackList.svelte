<script lang="ts">
	import type { Attachment } from 'svelte/attachments';
	import Icon from '#lib/Icon.svelte';
	import Waveform from '#lib/Waveform.svelte';
	import { artworkGradient } from '#lib/artwork.js';
	import { canChange } from '#lib/permissions.js';
	import { player } from '#lib/player.svelte.js';
	import { session } from '#lib/session.svelte.js';
	import TrackService, {
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

	/** Polls a pending waveform this often, this many times: a fresh upload gets its waveform within seconds. */
	const POLL_INTERVAL = 3000;
	const POLL_ATTEMPTS = 10;

	/** The waveforms fetched so far, by track id. */
	let waveforms = $state<Record<string, TrackWaveform>>({});
	/** Tracks whose waveform has been asked for, so each is fetched once. Bookkeeping only: nothing renders it. */
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- deliberately not reactive
	const requested = new Set<string>();
	let alive = true;
	$effect(() => () => {
		alive = false;
	});

	function loadWaveform(trackId: string, attempt = 1) {
		trackService.findWaveform(trackId).then((waveform) => {
			if (!alive || !waveform) return;
			waveforms[trackId] = waveform;
			if (waveform.status === 'PENDING' && attempt < POLL_ATTEMPTS) {
				setTimeout(() => alive && loadWaveform(trackId, attempt + 1), POLL_INTERVAL);
			}
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
		player.playFrom(tracks, index, fraction);
	}

	/** Track ids whose description is shown. */
	let expanded = $state<Record<string, boolean>>({});

	/** The id of the track being edited, and the form's values; one track at a time. */
	let editing = $state<string | null>(null);
	let form = $state<TrackEdit>({ title: '', description: '', artist: '', album: '' });
	let saving = $state(false);
	/** Error message per track id, for the last refused change. */
	let errors = $state<Record<string, string>>({});

	function startEdit(track: Track) {
		editing = track.id;
		form = {
			title: track.title ?? '',
			description: track.description ?? '',
			artist: track.artist ?? '',
			album: track.album ?? ''
		};
		errors[track.id] = '';
	}

	function cancelEdit() {
		editing = null;
	}

	async function saveEdit(track: Track) {
		saving = true;
		try {
			const updated = await trackService.update(track.id, {
				title: form.title,
				description: form.description,
				// An empty field clears the artist or album
				artist: form.artist || null,
				album: form.album || null
			});
			editing = null;
			onupdated?.(updated);
		} catch (e) {
			errors[track.id] = (e as Error).message;
		} finally {
			saving = false;
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
		<p class="card p-6 text-center text-ink-muted">No tracks found.</p>
	{:else}
		{#each tracks as track, index (track.id)}
			{@const playing = player.isPlaying(track.id)}
			{@const loaded = player.isLoaded(track.id)}
			<article
				data-testid="track-{track.id}"
				class="card flex gap-4 p-4 transition-shadow hover:shadow-md"
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
							class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-accent text-white shadow-sm transition-colors hover:bg-accent-strong"
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
								class="truncate font-medium {loaded ? 'text-accent-strong' : ''}"
								data-testid="track-title"
							>
								{track.title}
							</h2>
						</div>

						{#if canChange(session.account, track)}
							<div class="flex shrink-0 gap-2">
								<button
									type="button"
									aria-label="edit track {track.title}"
									class="rounded-full border border-line px-3 py-1 text-xs font-medium hover:border-ink-muted"
									onclick={() => startEdit(track)}>Edit</button
								>
								<button
									type="button"
									aria-label="delete track {track.title}"
									class="rounded-full border border-red-200 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
									onclick={() => deleteTrack(track)}>Delete</button
								>
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
								class="rounded-full bg-canvas px-2.5 py-0.5 text-xs font-medium text-ink-muted hover:bg-accent-soft hover:text-accent-strong"
								onclick={() => ontagclick?.(tag)}>#{tag}</button
							>
						{/each}
						<button
							type="button"
							class="ml-auto inline-flex items-center gap-1 text-xs font-medium text-ink-muted hover:text-ink"
							aria-label="show track {track.title} detail button"
							aria-expanded={!!expanded[track.id]}
							onclick={() => (expanded[track.id] = !expanded[track.id])}
						>
							{expanded[track.id] ? 'Less' : 'More'}
							<svg
								class="h-3 w-3 fill-current transition-transform {expanded[track.id]
									? '-rotate-90'
									: 'rotate-90'}"
								viewBox="0 0 20 20"
								aria-hidden="true"
							>
								<path
									d="M12.95 10.707l.707-.707L8 4.343 6.586 5.757 10.828 10l-4.242 4.243L8 15.657l4.95-4.95z"
								/>
							</svg>
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
							<label class="block text-sm font-medium"
								>Title <input class={inputClass} required bind:value={form.title} /></label
							>
							<label class="block text-sm font-medium"
								>Description <textarea class={inputClass} required bind:value={form.description}
								></textarea></label
							>
							<div class="grid gap-3 sm:grid-cols-2">
								<label class="block text-sm font-medium"
									>Artist <input class={inputClass} bind:value={form.artist} /></label
								>
								<label class="block text-sm font-medium"
									>Album <input class={inputClass} bind:value={form.album} /></label
								>
							</div>
							<div class="flex gap-2">
								<button type="submit" class="btn-accent" disabled={saving}>Save</button>
								<button type="button" class="btn-quiet" onclick={cancelEdit}>Cancel</button>
							</div>
						</form>
					{/if}

					{#if errors[track.id]}
						<p role="alert" class="mt-2 text-sm text-red-700">{errors[track.id]}</p>
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
