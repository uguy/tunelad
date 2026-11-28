<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { AlbumError, AlbumService } from '#lib/albums.svelte.js';
	import { session } from '#lib/session.svelte.js';

	const service = new AlbumService();

	let title = $state('');
	let artist = $state('');
	let releaseDate = $state('');
	let busy = $state(false);
	let message = $state('');
	let refusedWith = $state<number | null>(null);

	async function create(event: SubmitEvent) {
		event.preventDefault();
		busy = true;
		message = '';
		try {
			const album = await service.create({ title, artist, releaseDate });
			await goto(resolve('/albums/[id]', { id: album.id }));
		} catch (e) {
			refusedWith = e instanceof AlbumError ? e.status : 0;
			message = e instanceof AlbumError ? e.message : 'The server could not be reached.';
		} finally {
			busy = false;
		}
	}

	const labelClass = 'block text-sm font-medium';
	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
</script>

<svelte:head>
	<title>Tune Lad - Add an album</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-2xl flex-col gap-6">
	<div>
		<a href={resolve('/albums')} class="text-sm font-medium text-ink-muted hover:text-ink">
			← Albums
		</a>
		<h1 class="mt-2 text-3xl font-bold tracking-tight">Add an album</h1>
	</div>

	{#if session.account}
		<form class="card space-y-4 p-6" aria-label="add an album" onsubmit={create}>
			<label class={labelClass}>
				Title <input class={inputClass} name="title" required bind:value={title} />
			</label>
			<label class={labelClass}>
				Artist <input class={inputClass} name="artist" required bind:value={artist} />
			</label>
			<label class={labelClass}>
				Release date (optional)
				<input class={inputClass} type="date" name="releaseDate" bind:value={releaseDate} />
			</label>
			{#if message}
				<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
					{message}
					{#if refusedWith === 401}<a href="/login" rel="external" class="font-medium underline">
							Log in
						</a>{/if}
				</p>
			{/if}
			<button type="submit" class="btn-accent" disabled={busy}>Create</button>
		</form>
	{:else if session.loaded}
		<p class="card p-6">
			<a href="/login" rel="external" class="font-medium underline">Log in</a> to add albums.
		</p>
	{/if}
</section>
