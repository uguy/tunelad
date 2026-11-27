<script lang="ts">
	import Icon from './Icon.svelte';
	import { favourites } from './favourites.svelte.js';
	import { session } from './session.svelte.js';

	let {
		trackId,
		title,
		class: className = ''
	}: { trackId: string; title: string; class?: string } = $props();

	const marked = $derived(favourites.has(trackId));
</script>

<!-- Only for a logged-in user: favourites belong to an account -->
{#if session.account}
	<button
		type="button"
		aria-pressed={marked}
		aria-label="favourite track {title} button"
		title={marked ? 'Remove from favourites' : 'Add to favourites'}
		class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition-colors hover:bg-canvas {marked
			? 'text-accent-ink'
			: 'text-ink-muted hover:text-ink'} {className}"
		onclick={() => favourites.toggle(trackId)}
	>
		<Icon name={marked ? 'heart' : 'heartOutline'} />
	</button>
{/if}
