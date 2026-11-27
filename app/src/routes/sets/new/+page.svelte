<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { session } from '#lib/session.svelte.js';
	import type { Tune } from '../../tunes/Tunes.js';
	import SetBuilder from '../SetBuilder.svelte';
	import SetService, { SetError } from '../Sets.js';

	const setService = new SetService();

	/** The tunes of the set being built, in order; a tune may be added twice. */
	let chosen = $state.raw<Tune[]>([]);
	let busy = $state(false);
	let message = $state('');
	let refusedWith = $state<number | null>(null);

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
		<SetBuilder bind:chosen />
		<div>
			<button
				type="button"
				class="btn-accent"
				disabled={busy || chosen.length === 0}
				onclick={create}
			>
				Create the set
			</button>
		</div>

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
