<script lang="ts">
	import { resolve } from '$app/paths';
	import Icon from './Icon.svelte';
	import { artworkGradient, formatTime } from './artwork.js';
	import { player, type MediaSource } from './player.svelte.js';

	/** The outcome of the last copy, for the source it was made on: a new source drops it. */
	type CopyResult = { source: MediaSource } & (
		{ status: 'copied' } | { status: 'failed'; fallbackLink: string }
	);
	// raw: kept by reference, so that it can be compared with the current source
	let lastCopy = $state.raw<CopyResult | null>(null);
	const copy = $derived(lastCopy?.source === player.source ? lastCopy : null);

	// A media fragment (#t=) makes the browser start at that time, fetching the matching byte range.
	const src = $derived(
		player.source
			? `/api/tracks/${player.source.trackId}/play` +
					(player.source.start > 0 ? `#t=${player.source.start}` : '')
			: ''
	);

	const title = $derived(player.track?.title ?? 'Loading…');
	const owner = $derived(player.track?.owner?.displayName);

	async function copyLinkAtCurrentTime() {
		if (!player.source) return;
		const copiedSource = player.source;
		const link = new URL(
			`${resolve('/tracks')}?play=${encodeURIComponent(copiedSource.trackId)}&t=${Math.floor(player.currentTime)}`,
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

	const iconButton =
		'inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-ink transition-colors hover:text-accent-strong disabled:opacity-30 disabled:hover:text-ink';
</script>

<!-- The app's one audio element: the controls below drive it through the player state -->
<audio
	id="player"
	{src}
	preload="none"
	autoplay
	bind:currentTime={player.currentTime}
	bind:paused={player.paused}
	bind:volume={player.volume}
	bind:muted={player.muted}
	onloadedmetadata={(e) => player.metadataLoaded(e.currentTarget.duration)}
	onended={() => player.ended()}
></audio>

<section
	aria-label="player"
	class="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 shadow-[0_-2px_10px_rgba(0,0,0,0.06)] backdrop-blur"
>
	<!-- On small screens, the progress is a thin bar along the top edge -->
	<div class="h-0.5 bg-line sm:hidden" aria-hidden="true">
		<div
			class="h-full bg-accent"
			style:width="{player.progressOf(player.source?.trackId ?? '') * 100}%"
		></div>
	</div>

	<div class="mx-auto flex h-16 max-w-6xl items-center gap-2 px-3 sm:gap-4 sm:px-6 lg:px-8">
		<div class="flex items-center gap-1">
			<button
				type="button"
				class="{iconButton} hidden sm:inline-flex"
				aria-label="Previous track"
				disabled={!player.hasPrevious}
				onclick={() => player.previous()}><Icon name="previous" /></button
			>
			<button
				type="button"
				class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent text-white transition-colors hover:bg-accent-strong"
				aria-label={player.paused ? 'Play' : 'Pause'}
				onclick={() => player.toggle()}
				><Icon name={player.paused ? 'play' : 'pause'} class="h-5 w-5" /></button
			>
			<button
				type="button"
				class={iconButton}
				aria-label="Next track"
				disabled={!player.hasNext}
				onclick={() => player.next()}><Icon name="next" /></button
			>
		</div>

		<div class="hidden min-w-0 flex-1 items-center gap-3 sm:flex">
			<span class="w-12 text-right text-xs tabular-nums text-accent-strong" data-testid="elapsed"
				>{formatTime(player.currentTime)}</span
			>
			<input
				type="range"
				aria-label="seek"
				class="h-1 min-w-0 flex-1 cursor-pointer accent-accent"
				min="0"
				max={player.duration || 0}
				step="0.1"
				value={player.currentTime}
				disabled={!player.duration}
				oninput={(e) => player.seek(Number(e.currentTarget.value))}
			/>
			<span class="w-12 text-xs tabular-nums text-ink-muted" data-testid="duration"
				>{formatTime(player.duration)}</span
			>
		</div>

		<div class="group hidden items-center md:flex">
			<button
				type="button"
				class={iconButton}
				aria-label={player.muted ? 'Unmute' : 'Mute'}
				onclick={() => player.toggleMute()}
				><Icon name={player.muted || player.volume === 0 ? 'muted' : 'volume'} /></button
			>
			<input
				type="range"
				aria-label="volume"
				class="h-1 w-20 cursor-pointer accent-accent"
				min="0"
				max="1"
				step="0.01"
				value={player.muted ? 0 : player.volume}
				oninput={(e) => player.setVolume(Number(e.currentTarget.value))}
			/>
		</div>

		<div class="flex min-w-0 flex-1 items-center gap-3 sm:w-64 sm:flex-none">
			<div
				class="h-10 w-10 shrink-0 rounded-sm"
				style:background-image={artworkGradient(player.source?.trackId ?? '')}
				aria-hidden="true"
			></div>
			<div class="min-w-0 leading-tight">
				{#if owner}
					<p class="truncate text-xs text-ink-muted" data-testid="player-owner">{owner}</p>
				{/if}
				<p class="truncate text-sm font-medium" data-testid="player-title">{title}</p>
			</div>
		</div>

		<div class="flex items-center gap-2">
			<button
				id="copy-link-at-current-time-btn"
				type="button"
				class={iconButton}
				aria-label="Copy link at current time"
				title="Copy link at current time"
				onclick={copyLinkAtCurrentTime}><Icon name="link" /></button
			>
			{#if copy?.status === 'copied'}
				<span role="status" class="text-xs whitespace-nowrap text-ink-muted">Link copied</span>
			{/if}
		</div>
	</div>

	{#if copy?.status === 'failed'}
		<div class="mx-auto max-w-6xl px-3 pb-2 sm:px-6 lg:px-8">
			<input
				aria-label="link at current time"
				class="w-full rounded border-line px-2 py-1 text-sm"
				readonly
				value={copy.fallbackLink}
				onfocus={(e) => e.currentTarget.select()}
			/>
		</div>
	{/if}
</section>
