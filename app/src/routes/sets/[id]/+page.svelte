<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import Comments from '#lib/Comments.svelte';
	import { live } from '#lib/live.svelte.js';
	import { refreshOnLive } from '#lib/liveRefresh.js';
	import SetService, { type TuneSet } from '../Sets.js';

	const service = new SetService();
	const id = $derived(page.params.id ?? '');

	let set = $state.raw<TuneSet | null>(null);
	let status = $state<'loading' | 'ready' | 'notfound' | 'error'>('loading');

	async function load() {
		try {
			set = await service.findOne(id);
			status = set ? 'ready' : 'notfound';
		} catch {
			status = 'error';
		}
	}

	onMount(load);

	// What was missed while the live connection was down; the page updates in place
	onMount(() => refreshOnLive(live, ['set.changed'], () => void load()));
</script>

<svelte:head>
	<title>Tune Lad - Set</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<a href={resolve('/sets')} class="text-sm font-medium text-ink-muted hover:text-ink">← Sets</a>

	{#if status === 'loading'}
		<p class="text-ink-muted" role="status">Loading…</p>
	{:else if status === 'notfound'}
		<h1 class="text-3xl font-bold tracking-tight">Set not found</h1>
	{:else if status === 'error' || !set}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The set could not be loaded.
		</p>
	{:else}
		<h1 class="text-3xl font-bold tracking-tight">Set</h1>
		{#if page.state.existed}
			<p role="status" class="rounded-md bg-surface-muted px-3 py-2 text-sm">
				This set already existed: here it is.
			</p>
		{/if}
		<ol class="card divide-y divide-line" aria-label="tunes of the set">
			{#each set.tunes as tune, i (i)}
				<li>
					<a
						href={resolve('/tunes/[id]', { id: tune.id })}
						class="flex items-baseline justify-between gap-3 px-4 py-3 hover:bg-surface-muted"
					>
						<span class="font-medium">
							<span class="mr-2 text-ink-muted tabular-nums">{i + 1}.</span>{tune.name}
						</span>
						<span class="text-sm text-ink-muted capitalize">{tune.danceType}</span>
					</a>
				</li>
			{/each}
		</ol>
		<Comments subject="sets" {id} />
	{/if}
</section>
