<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		children
	}: {
		title: string;
		/** What to do next: a sentence, a link or a button. */
		children?: Snippet;
	} = $props();
</script>

<div class="card flex flex-col items-center gap-3 px-6 py-12 text-center">
	<div class="h-1 w-10 rounded-full bg-accent" aria-hidden="true"></div>
	<h2 class="text-lg font-semibold tracking-tight">{title}</h2>
	{#if children}
		<div class="max-w-md text-sm text-ink-muted">{@render children()}</div>
	{/if}
</div>
