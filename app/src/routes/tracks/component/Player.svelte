<script lang="ts">
	import { resolve } from '$app/paths';
	import type { MediaSource } from '#lib/player.svelte.js';

	interface Props {
		id?: string;
		source?: MediaSource | null;
		autoplay?: boolean;
	}

	let { id = 'player', source = null, autoplay = true }: Props = $props();

	let currentTime = $state(0);

	/** The outcome of the last copy, for the source it was made on: a new source drops it. */
	type CopyResult = { source: MediaSource } & (
		{ status: 'copied' } | { status: 'failed'; fallbackLink: string }
	);
	// raw: kept by reference, so that it can be compared with the current source
	let lastCopy = $state.raw<CopyResult | null>(null);
	const copy = $derived(lastCopy?.source === source ? lastCopy : null);

	// A media fragment (#t=) makes the browser start at that time, fetching the matching byte range.
	const src = $derived(
		source
			? `/api/tracks/${source.trackId}/play` + (source.start > 0 ? `#t=${source.start}` : '')
			: ''
	);

	async function copyLinkAtCurrentTime() {
		if (!source) return;
		const copiedSource = source;
		const link = new URL(
			`${resolve('/tracks')}?play=${encodeURIComponent(source.trackId)}&t=${Math.floor(currentTime)}`,
			location.origin
		).href;
		try {
			await navigator.clipboard.writeText(link);
			lastCopy = { source: copiedSource, status: 'copied' };
		} catch {
			// No clipboard (insecure context or permission denied): show the link to copy by hand.
			lastCopy = { source: copiedSource, status: 'failed', fallbackLink: link };
		}
	}
</script>

<audio {id} {src} controls preload="none" class="w-full" {autoplay} bind:currentTime></audio>

{#if source}
	<div class="flex flex-wrap items-center gap-2 mt-2 text-sm">
		<button
			id="copy-link-at-current-time-btn"
			type="button"
			class="bg-gray-900 text-white px-3 py-1 rounded-md font-medium"
			onclick={copyLinkAtCurrentTime}>Copy link at current time</button
		>
		{#if copy?.status === 'copied'}
			<span role="status">Link copied</span>
		{:else if copy?.status === 'failed'}
			<input
				aria-label="link at current time"
				class="flex-1 min-w-0 border rounded px-2 py-1"
				readonly
				value={copy.fallbackLink}
				onfocus={(e) => e.currentTarget.select()}
			/>
		{/if}
	</div>
{/if}
