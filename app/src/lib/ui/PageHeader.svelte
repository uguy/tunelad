<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		subtitle,
		actions
	}: {
		title: string;
		subtitle?: string;
		/** Controls at the end of the title row: a button or a link. */
		actions?: Snippet;
	} = $props();
</script>

<div class="flex flex-wrap items-end justify-between gap-4">
	<div class="min-w-0">
		<h1 class="text-3xl font-bold tracking-tight text-balance">{title}</h1>
		{#if subtitle}
			<p class="mt-1 text-ink-muted">{subtitle}</p>
		{/if}
	</div>
	{#if actions}
		<div class="flex shrink-0 items-center gap-2">{@render actions()}</div>
	{/if}
</div>
