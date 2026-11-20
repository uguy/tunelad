<script lang="ts">
	import me from '#lib/store/me.js';
	import { canChange } from '#lib/permissions.js';
	import TrackService, { type Track, type TrackEdit } from '../Tracks.js';

	interface Props {
		tracks?: Track[];
		/** The play button of a track was clicked. */
		onplay?: (trackId: string) => void;
		/** A tag of a track was clicked. */
		ontagclick?: (tag: string) => void;
		/** A track was edited: `track` is the track as the server now has it. */
		onupdated?: (track: Track) => void;
		/** A track was deleted. */
		ondeleted?: (trackId: string) => void;
	}

	let { tracks = [], onplay, ontagclick, onupdated, ondeleted }: Props = $props();

	const trackService = new TrackService();

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

	/** Runs the action without letting the click toggle the enclosing `<details>`. */
	function inSummary(action: () => void) {
		return (event: MouseEvent) => {
			event.preventDefault();
			event.stopPropagation();
			action();
		};
	}
</script>

<div id="track-list" class="flex flex-col w-full">
	{#if tracks.length === 0}
		<div>No tracks found.</div>
	{:else}
		{#each tracks as track (track.id)}
			<div data-testid="track-{track.id}">
				<details class="w-full p-4 border-b border-grey-lighter hover:shadow-lg">
					<summary class="flex items-center w-full hover:underline">
						<button
							id="play-track-{track.id}-btn"
							aria-label="play track {track.title} button"
							type="button"
							onclick={() => onplay?.(track.id)}
						>
							<svg
								xmlns="http://www.w3.org/2000/svg"
								viewBox="0 0 24 24"
								fill="currentColor"
								class="w-6 h-6 text-2xl text-gray-400"
							>
								<path
									fill-rule="evenodd"
									d="M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm14.024-.983a1.125 1.125 0 010 1.966l-5.603 3.113A1.125 1.125 0 019 15.113V8.887c0-.857.921-1.4 1.671-.983l5.603 3.113z"
									clip-rule="evenodd"
								/>
							</svg>
						</button>
						<span class="px-2">{track.title}</span>
						{#if track.owner}
							<span class="text-sm text-gray-500" data-testid="track-owner"
								>by {track.owner.displayName}</span
							>
						{/if}

						{#if canChange($me, track)}
							<span class="ml-auto flex gap-2">
								<button
									type="button"
									aria-label="edit track {track.title}"
									class="text-sm px-2 py-1 rounded bg-gray-200 hover:bg-gray-300"
									onclick={inSummary(() => startEdit(track))}>Edit</button
								>
								<button
									type="button"
									aria-label="delete track {track.title}"
									class="text-sm px-2 py-1 rounded bg-red-100 text-red-800 hover:bg-red-200"
									onclick={inSummary(() => deleteTrack(track))}>Delete</button
								>
							</span>
						{/if}

						<button
							type="button"
							class="btn-detail {canChange($me, track) ? 'ml-2' : 'ml-auto'}"
							aria-label="show track {track.title} detail button"
						>
							<svg
								class="fill-current opacity-75 w-4 h-4 -mr-1"
								xmlns="http://www.w3.org/2000/svg"
								viewBox="0 0 20 20"
							>
								<path
									d="M12.95 10.707l.707-.707L8 4.343 6.586 5.757 10.828 10l-4.242 4.243L8 15.657l4.95-4.95z"
								/>
							</svg>
						</button>
					</summary>
					<div class="mt-4 leading-normal text-md">
						{#if track.album}
							<b>Album : {track.album}</b>
						{/if}
						<p class="my-2 text-sm">{track.description}</p>
					</div>
					<div class="card-footer flex flex-wrap">
						{#each track.tags ?? [] as tag (tag)}
							<button
								type="button"
								class="text-sm font-medium bg-gray-300 rounded-lg mr-2 my-1 px-2 py-1 hover:cursor-pointer"
								onclick={() => ontagclick?.(tag)}>{tag}</button
							>
						{/each}
					</div>
				</details>
				{#if editing === track.id}
					<form
						class="p-4 space-y-2 border-b border-grey-lighter"
						aria-label="edit track {track.title}"
						onsubmit={(event) => {
							event.preventDefault();
							saveEdit(track);
						}}
					>
						<label class="block text-sm"
							>Title <input class="block w-full" required bind:value={form.title} /></label
						>
						<label class="block text-sm"
							>Description <textarea class="block w-full" required bind:value={form.description}
							></textarea></label
						>
						<label class="block text-sm"
							>Artist <input class="block w-full" bind:value={form.artist} /></label
						>
						<label class="block text-sm"
							>Album <input class="block w-full" bind:value={form.album} /></label
						>
						<div class="flex gap-2">
							<button
								type="submit"
								class="text-sm px-3 py-1 rounded bg-gray-900 text-white"
								disabled={saving}>Save</button
							>
							<button
								type="button"
								class="text-sm px-3 py-1 rounded bg-gray-200"
								onclick={cancelEdit}>Cancel</button
							>
						</div>
					</form>
				{/if}
				{#if errors[track.id]}
					<p role="alert" class="px-4 py-2 text-sm text-red-700">{errors[track.id]}</p>
				{/if}
			</div>
		{/each}
	{/if}
</div>

<style lang="postcss">
	@reference "tailwindcss";

	details {
		user-select: none;
	}

	details > summary > button.btn-detail > svg {
		transform: rotate(90deg);
	}

	details[open] > summary > button.btn-detail > svg {
		transform: rotate(-90deg);
	}

	details[open] > summary ~ * {
		animation: ease-opacity-t-b 0.5s ease;
	}

	summary {
		cursor: pointer;
	}

	svg {
		transition: all 0.3s;
	}

	summary::-webkit-details-marker {
		display: none;
	}

	@keyframes ease-opacity-t-b {
		from {
			opacity: 0;
			transform: translateY(-0.5rem);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}
</style>
