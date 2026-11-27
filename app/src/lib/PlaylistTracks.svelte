<script lang="ts">
	import Icon from './Icon.svelte';
	import { artworkGradient } from './artwork.js';
	import { dropIndex, inLowerHalf, moveItem } from './playlistOrder.js';
	import { player } from './player.svelte.js';
	import type { Track } from '../routes/tracks/Tracks.js';

	interface Props {
		/** The playlist's tracks, in order. */
		tracks: Track[];
		/** The user asked for this order: the ids of the tracks, in order. */
		onreorder: (trackIds: string[]) => void;
		onremove: (track: Track) => void;
		/** Plays the track at that index, with the playlist as the player's queue. */
		onplay: (index: number) => void;
	}

	let { tracks, onreorder, onremove, onplay }: Props = $props();

	/** The index of the row being dragged, and where it would land. */
	let dragging = $state<number | null>(null);
	let target = $state<{ index: number; after: boolean } | null>(null);
	/** What screen readers hear after a move: a move by buttons moves the focus' row out from under it. */
	let announcement = $state('');

	const ids = $derived(tracks.map((t) => t.id));

	function move(from: number, to: number) {
		if (to === from) return;
		onreorder(moveItem(ids, from, to));
		announcement = `Moved ${tracks[from].title} to position ${to + 1} of ${tracks.length}.`;
	}

	function onDragStart(event: DragEvent, index: number) {
		dragging = index;
		if (event.dataTransfer) {
			event.dataTransfer.effectAllowed = 'move';
			// Firefox starts no drag without data
			event.dataTransfer.setData('text/plain', tracks[index].id);
		}
	}

	function onDragOver(event: DragEvent, index: number) {
		if (dragging === null) return;
		event.preventDefault(); // allows the drop
		if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
		const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
		target = { index, after: inLowerHalf(event.clientY, box) };
	}

	function onDrop(event: DragEvent) {
		event.preventDefault();
		if (dragging !== null && target) {
			const to = dropIndex(dragging, target.index, target.after);
			if (to !== dragging) onreorder(moveItem(ids, dragging, to));
		}
		endDrag();
	}

	function endDrag() {
		dragging = null;
		target = null;
	}

	const buttonClass =
		'inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-ink-muted transition-colors hover:bg-canvas hover:text-ink disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-ink-muted';
</script>

<ol class="flex flex-col gap-2" aria-label="tracks of the playlist">
	{#each tracks as track, index (track.id)}
		{@const playing = player.isPlaying(track.id)}
		{@const loaded = player.isLoaded(track.id)}
		{@const here = target?.index === index && dragging !== null && dragging !== index}
		<li
			data-testid="playlist-track-{track.id}"
			draggable="true"
			class="card flex items-center gap-3 border-l-4 p-3 transition-opacity {loaded
				? 'border-accent'
				: 'border-transparent'} {dragging === index ? 'opacity-40' : ''} {here && !target?.after
				? 'shadow-[inset_0_3px_0_0_var(--accent)]'
				: ''} {here && target?.after ? 'shadow-[inset_0_-3px_0_0_var(--accent)]' : ''}"
			ondragstart={(e) => onDragStart(e, index)}
			ondragover={(e) => onDragOver(e, index)}
			ondrop={onDrop}
			ondragend={endDrag}
		>
			<span class="cursor-grab text-ink-muted" aria-hidden="true" title="Drag to reorder">
				<Icon name="grip" />
			</span>
			<span class="w-5 shrink-0 text-right text-sm text-ink-muted tabular-nums" aria-hidden="true">
				{index + 1}
			</span>
			<div
				class="hidden h-10 w-10 shrink-0 rounded-sm sm:block"
				style:background-image={artworkGradient(track.id)}
				aria-hidden="true"
			></div>
			<div class="min-w-0 flex-1 leading-tight">
				{#if track.owner}
					<p class="truncate text-sm text-ink-muted">{track.owner.displayName}</p>
				{/if}
				<p class="truncate font-medium {loaded ? 'text-accent-ink' : ''}" data-testid="track-title">
					{track.title}
				</p>
			</div>
			<button
				type="button"
				aria-label="{playing ? 'pause' : 'play'} track {track.title} button"
				class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent text-on-accent transition-colors hover:bg-accent-strong"
				onclick={() => onplay(index)}
			>
				<Icon name={playing ? 'pause' : 'play'} />
			</button>
			<div class="flex shrink-0 items-center">
				<button
					type="button"
					class={buttonClass}
					aria-label="Move {track.title} up"
					disabled={index === 0}
					onclick={() => move(index, index - 1)}
				>
					<Icon name="up" class="h-4 w-4" />
				</button>
				<button
					type="button"
					class={buttonClass}
					aria-label="Move {track.title} down"
					disabled={index === tracks.length - 1}
					onclick={() => move(index, index + 1)}
				>
					<Icon name="down" class="h-4 w-4" />
				</button>
				<button
					type="button"
					class={buttonClass}
					aria-label="Remove {track.title} from the playlist"
					onclick={() => onremove(track)}
				>
					<Icon name="trash" class="h-4 w-4" />
				</button>
			</div>
		</li>
	{/each}
</ol>

<!-- Heard, not seen: where a track went when it was moved with the buttons -->
<div class="sr-only" aria-live="polite">{announcement}</div>
