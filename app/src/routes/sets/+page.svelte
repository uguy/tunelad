<script lang="ts">
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { session } from '#lib/session.svelte.js';
	import { live } from '#lib/live.svelte.js';
	import { refreshOnLive } from '#lib/liveRefresh.js';
	import SetService, { type TuneSet } from './Sets.js';

	const service = new SetService();

	let sets = $state.raw<TuneSet[]>([]);
	let status = $state<'loading' | 'ready' | 'error'>('loading');

	async function load() {
		try {
			sets = await service.findAll();
			status = 'ready';
		} catch {
			status = 'error';
		}
	}

	onMount(load);

	// Sets created elsewhere, and what was missed while the live connection was down
	onMount(() => refreshOnLive(live, ['set.changed'], () => void load()));
</script>

<svelte:head>
	<title>Tune Lad - Sets</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<div class="flex items-center justify-between gap-3">
		<h1 class="text-3xl font-bold tracking-tight">Sets</h1>
		{#if session.account}
			<a href={resolve('/sets/new')} class="btn-accent">Build a set</a>
		{/if}
	</div>

	{#if status === 'error'}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The sets could not be loaded.
		</p>
	{:else if status === 'ready' && sets.length === 0}
		<p class="card p-6 text-ink-muted">No sets yet.</p>
	{:else}
		<ul class="card divide-y divide-line" aria-label="sets">
			{#each sets as set (set.id)}
				<li>
					<a
						href={resolve('/sets/[id]', { id: set.id })}
						class="block px-4 py-3 hover:bg-surface-muted"
					>
						<ol class="flex flex-wrap gap-x-2 font-medium" data-testid="set-tunes">
							{#each set.tunes as tune, i (i)}
								<li>{tune.name}{i < set.tunes.length - 1 ? ' ·' : ''}</li>
							{/each}
						</ol>
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</section>
