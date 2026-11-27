<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { moveItem } from '#lib/playlistOrder.js';
	import { session } from '#lib/session.svelte.js';
	import TuneService, { type Tune } from '../../tunes/Tunes.js';
	import SetService, { MAX_TUNES, SetError } from '../Sets.js';

	const tuneService = new TuneService();
	const setService = new SetService();

	let text = $state('');
	let found = $state.raw<Tune[]>([]);
	/** The tunes of the set being built, in order; a tune may be added twice. */
	let chosen = $state.raw<Tune[]>([]);
	/** What assistive technology is told after each change. */
	let announcement = $state('');
	let busy = $state(false);
	let message = $state('');
	let refusedWith = $state<number | null>(null);

	async function search() {
		try {
			found = await tuneService.findAll('', text);
		} catch {
			found = [];
		}
	}
	onMount(search);

	function add(tune: Tune) {
		if (chosen.length >= MAX_TUNES) {
			announcement = `A set has at most ${MAX_TUNES} tunes.`;
			return;
		}
		chosen = [...chosen, tune];
		announcement = `${tune.name} added at position ${chosen.length}.`;
	}

	function move(index: number, by: -1 | 1) {
		const tune = chosen[index];
		chosen = moveItem(chosen, index, index + by);
		announcement = `${tune.name} moved to position ${index + by + 1} of ${chosen.length}.`;
	}

	function remove(index: number) {
		const tune = chosen[index];
		chosen = chosen.filter((_, i) => i !== index);
		announcement = `${tune.name} removed.`;
	}

	async function create() {
		busy = true;
		message = '';
		try {
			const { set, created } = await setService.create(chosen.map((tune) => tune.id));
			await goto(resolve('/sets/[id]', { id: set.id }), { state: { existed: !created } });
		} catch (e) {
			refusedWith = e instanceof SetError ? e.status : 0;
			message = e instanceof SetError ? e.message : 'The server could not be reached.';
		} finally {
			busy = false;
		}
	}

	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
	const iconButton =
		'inline-flex size-9 items-center justify-center rounded-full border border-line text-sm hover:border-ink-muted disabled:opacity-40';
</script>

<svelte:head>
	<title>Tune Lad - Build a set</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<div>
		<a href={resolve('/sets')} class="text-sm font-medium text-ink-muted hover:text-ink">← Sets</a>
		<h1 class="mt-2 text-3xl font-bold tracking-tight">Build a set</h1>
	</div>

	{#if session.account}
		<div class="grid gap-6 md:grid-cols-2">
			<div class="card space-y-3 p-4">
				<h2 class="text-lg font-semibold">Find tunes</h2>
				<label class="block text-sm font-medium">
					Search
					<input class={inputClass} type="search" bind:value={text} oninput={search} />
				</label>
				<ul class="divide-y divide-line" aria-label="tunes found">
					{#each found as tune (tune.id)}
						<li class="flex items-center justify-between gap-2 py-2">
							<span>
								{tune.name} <span class="text-sm text-ink-muted capitalize">{tune.danceType}</span>
							</span>
							<button
								type="button"
								class="btn-quiet"
								onclick={() => add(tune)}
								aria-label="Add {tune.name}"
							>
								Add
							</button>
						</li>
					{/each}
				</ul>
			</div>

			<div class="card space-y-3 p-4">
				<h2 class="text-lg font-semibold">The set</h2>
				{#if chosen.length === 0}
					<p class="text-ink-muted">Add tunes from the list.</p>
				{:else}
					<ol class="divide-y divide-line" aria-label="tunes of the set">
						{#each chosen as tune, i (i)}
							<li class="flex items-center justify-between gap-2 py-2">
								<span><span class="text-ink-muted tabular-nums">{i + 1}.</span> {tune.name}</span>
								<span class="flex gap-1">
									<button
										type="button"
										class={iconButton}
										disabled={i === 0}
										onclick={() => move(i, -1)}
										aria-label="Move {tune.name} up"
									>
										<span aria-hidden="true">↑</span>
									</button>
									<button
										type="button"
										class={iconButton}
										disabled={i === chosen.length - 1}
										onclick={() => move(i, 1)}
										aria-label="Move {tune.name} down"
									>
										<span aria-hidden="true">↓</span>
									</button>
									<button
										type="button"
										class={iconButton}
										onclick={() => remove(i)}
										aria-label="Remove {tune.name}"
									>
										<span aria-hidden="true">×</span>
									</button>
								</span>
							</li>
						{/each}
					</ol>
				{/if}
				<button
					type="button"
					class="btn-accent"
					disabled={busy || chosen.length === 0}
					onclick={create}
				>
					Create the set
				</button>
			</div>
		</div>

		<p class="sr-only" role="status" aria-live="polite">{announcement}</p>

		{#if message}
			<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
				{message}
				{#if refusedWith === 401}<a href="/login" rel="external" class="font-medium underline">
						Log in
					</a>{/if}
			</p>
		{/if}
	{:else if session.loaded}
		<p class="card p-6">
			<a href="/login" rel="external" class="font-medium underline">Log in</a> to build sets.
		</p>
	{/if}
</section>
