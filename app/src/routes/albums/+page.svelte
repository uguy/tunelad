<script lang="ts">
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { albums, formatReleaseDate } from '#lib/albums.svelte.js';
	import { session } from '#lib/session.svelte.js';
	import EmptyState from '#lib/ui/EmptyState.svelte';
	import PageHeader from '#lib/ui/PageHeader.svelte';

	let text = $state('');

	onMount(() => {
		void albums.load('');
		return albums.follow();
	});
</script>

<svelte:head>
	<title>Tune Lad - Albums</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<PageHeader title="Albums" subtitle="Releases and the tracks on them, with or without audio.">
		{#snippet actions()}
			{#if session.account}
				<a href={resolve('/albums/new')} class="btn-accent">Add an album</a>
			{/if}
		{/snippet}
	</PageHeader>

	<form
		role="search"
		aria-label="search albums"
		onsubmit={(e) => {
			e.preventDefault();
			void albums.load(text);
		}}
	>
		<label class="block text-sm font-medium">
			Search by title or artist
			<input
				class="mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent"
				type="search"
				name="q"
				bind:value={text}
				oninput={() => void albums.load(text)}
			/>
		</label>
	</form>

	{#if albums.status === 'error'}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The albums could not be loaded.
		</p>
	{:else if albums.status === 'ready' && albums.albums.length === 0}
		<EmptyState title="No albums found">Try other words, or add an album of your own.</EmptyState>
	{:else}
		<ul class="card divide-y divide-line" aria-label="albums">
			{#each albums.albums as album (album.id)}
				<li>
					<a
						href={resolve('/albums/[id]', { id: album.id })}
						class="flex items-baseline justify-between gap-3 px-4 py-3 hover:bg-surface-muted"
					>
						<span class="min-w-0">
							<span class="font-medium" data-testid="album-title">{album.title}</span>
							<span class="text-sm text-ink-muted"> by {album.artist}</span>
						</span>
						{#if album.releaseDate}
							<span class="shrink-0 text-sm text-ink-muted">
								{formatReleaseDate(album.releaseDate)}
							</span>
						{/if}
					</a>
				</li>
			{/each}
		</ul>
	{/if}
</section>
