<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { session } from '#lib/session.svelte.js';
	import TuneService, { parseAliases, parseSourceId, TuneError, type Tune } from '../Tunes.js';

	const service = new TuneService();

	let danceTypes = $state.raw<string[]>([]);
	onMount(async () => (danceTypes = await service.danceTypes()));

	let source = $state('');
	let name = $state('');
	let danceType = $state('');
	let aliases = $state('');
	let author = $state('');
	let key = $state('');
	let abc = $state('');

	let busy = $state(false);
	let message = $state('');
	let refusedWith = $state<number | null>(null);

	async function run(action: () => Promise<Tune>) {
		busy = true;
		message = '';
		try {
			const tune = await action();
			await goto(resolve('/tunes/[id]', { id: tune.id }));
		} catch (e) {
			refusedWith = e instanceof TuneError ? e.status : 0;
			message = e instanceof TuneError ? e.message : 'The server could not be reached.';
		} finally {
			busy = false;
		}
	}

	function importTune(event: SubmitEvent) {
		event.preventDefault();
		const id = parseSourceId(source);
		if (id === null) {
			refusedWith = null;
			message = 'Enter a thesession.org tune number, or the address of its page.';
			return;
		}
		void run(() => service.importFrom(id));
	}

	function createTune(event: SubmitEvent) {
		event.preventDefault();
		void run(() =>
			service.create({
				name,
				danceType,
				aliases: parseAliases(aliases),
				author: author || undefined,
				key: key || undefined,
				abc: abc || undefined
			})
		);
	}

	const labelClass = 'block text-sm font-medium';
	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
</script>

<svelte:head>
	<title>Tune Lad - Add a tune</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-2xl flex-col gap-6">
	<div>
		<a href={resolve('/tunes')} class="text-sm font-medium text-ink-muted hover:text-ink">
			← Tunes
		</a>
		<h1 class="mt-2 text-3xl font-bold tracking-tight">Add a tune</h1>
	</div>

	{#if session.account}
		<form class="card space-y-4 p-6" aria-labelledby="import-heading" onsubmit={importTune}>
			<h2 id="import-heading" class="text-lg font-semibold">Import from thesession.org</h2>
			<label class={labelClass}>
				Tune number or address
				<input class={inputClass} name="source" placeholder="27" bind:value={source} />
			</label>
			<button type="submit" class="btn-accent" disabled={busy}>Import</button>
		</form>

		<form class="card space-y-4 p-6" aria-labelledby="create-heading" onsubmit={createTune}>
			<h2 id="create-heading" class="text-lg font-semibold">Or enter it by hand</h2>
			<label class={labelClass}>
				Name <input class={inputClass} name="name" required bind:value={name} />
			</label>
			<label class={labelClass}>
				Dance type
				<select class={inputClass} name="danceType" required bind:value={danceType}>
					<option value="" disabled>Choose…</option>
					{#each danceTypes as type (type)}<option value={type}>{type}</option>{/each}
				</select>
			</label>
			<label class={labelClass}>
				Other names (one per line)
				<textarea class={inputClass} rows="2" name="aliases" bind:value={aliases}></textarea>
			</label>
			<div class="grid gap-4 sm:grid-cols-2">
				<label class={labelClass}>
					Author <input class={inputClass} name="author" bind:value={author} />
				</label>
				<label class={labelClass}>
					Usual key <input class={inputClass} name="key" bind:value={key} />
				</label>
			</div>
			<label class={labelClass}>
				Reference transcription (ABC)
				<textarea class="{inputClass} font-mono" rows="6" name="abc" bind:value={abc}></textarea>
			</label>
			<button type="submit" class="btn-accent" disabled={busy}>Create</button>
		</form>

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
			<a href="/login" rel="external" class="font-medium underline">Log in</a> to add tunes.
		</p>
	{/if}
</section>
