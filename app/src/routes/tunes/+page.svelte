<script lang="ts">
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { session } from '#lib/session.svelte.js';
	import TuneService, { type Tune } from './Tunes.js';

	const service = new TuneService();

	let tunes = $state.raw<Tune[]>([]);
	let danceTypes = $state.raw<string[]>([]);
	let danceType = $state('');
	let text = $state('');
	let status = $state<'loading' | 'ready' | 'error'>('loading');

	async function refresh() {
		try {
			tunes = await service.findAll(danceType, text);
			status = 'ready';
		} catch {
			status = 'error';
		}
	}

	onMount(async () => {
		danceTypes = await service.danceTypes();
		await refresh();
	});

	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
</script>

<svelte:head>
	<title>Tune Lad - Tunes</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<div class="flex items-center justify-between gap-3">
		<h1 class="text-3xl font-bold tracking-tight">Tunes</h1>
		{#if session.account}
			<a href={resolve('/tunes/new')} class="btn-accent">Add a tune</a>
		{/if}
	</div>

	<form
		class="grid gap-4 sm:grid-cols-2"
		role="search"
		aria-label="filter tunes"
		onsubmit={(e) => {
			e.preventDefault();
			void refresh();
		}}
	>
		<label class="block text-sm font-medium">
			Search
			<input class={inputClass} type="search" name="q" bind:value={text} oninput={refresh} />
		</label>
		<label class="block text-sm font-medium">
			Dance type
			<select class={inputClass} name="danceType" bind:value={danceType} onchange={refresh}>
				<option value="">All</option>
				{#each danceTypes as type (type)}
					<option value={type}>{type}</option>
				{/each}
			</select>
		</label>
	</form>

	{#if status === 'error'}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The tunes could not be loaded.
		</p>
	{:else if status === 'ready' && tunes.length === 0}
		<p class="card p-6 text-ink-muted">No tunes found.</p>
	{:else}
		<ul class="card divide-y divide-line" aria-label="tunes">
			{#each tunes as tune (tune.id)}
				<li>
					<a
						href={resolve('/tunes/[id]', { id: tune.id })}
						class="flex items-baseline justify-between gap-3 px-4 py-3 hover:bg-surface-muted"
					>
						<span class="font-medium" data-testid="tune-name">{tune.name}</span>
						<span class="text-sm capitalize text-ink-muted">{tune.danceType}</span>
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</section>
