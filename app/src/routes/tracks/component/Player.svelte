<script lang="ts">
	import { resolve } from '$app/paths';
	import type { MediaSource } from '#lib/store/media-source.js';

	export let id = 'player';
	export let source: MediaSource | null = null;
	export let autoplay = true;

	let currentTime = 0;
	let copyStatus: 'idle' | 'copied' | 'failed' = 'idle';
	let fallbackLink = '';

	// A media fragment (#t=) makes the browser start at that time, fetching the matching byte range.
	$: src = source
		? `/api/tracks/${source.trackId}/play` + (source.start > 0 ? `#t=${source.start}` : '')
		: '';

	// Any new source resets the copy feedback.
	$: if (source) {
		copyStatus = 'idle';
		fallbackLink = '';
	}

	async function copyLinkAtCurrentTime() {
		if (!source) return;
		const link = new URL(
			`${resolve('/tracks')}?play=${encodeURIComponent(source.trackId)}&t=${Math.floor(currentTime)}`,
			location.origin
		).href;
		try {
			await navigator.clipboard.writeText(link);
			copyStatus = 'copied';
		} catch {
			// No clipboard (insecure context or permission denied): show the link to copy by hand.
			copyStatus = 'failed';
			fallbackLink = link;
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
			on:click={copyLinkAtCurrentTime}>Copy link at current time</button
		>
		{#if copyStatus === 'copied'}
			<span role="status">Link copied</span>
		{:else if copyStatus === 'failed'}
			<input
				aria-label="link at current time"
				class="flex-1 min-w-0 border rounded px-2 py-1"
				readonly
				value={fallbackLink}
				on:focus={(e) => e.currentTarget.select()}
			/>
		{/if}
	</div>
{/if}
