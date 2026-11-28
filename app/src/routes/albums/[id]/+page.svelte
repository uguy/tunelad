<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { AlbumService, formatReleaseDate, type Album } from '#lib/albums.svelte.js';
	import { live } from '#lib/live.svelte.js';
	import { refreshOnLive } from '#lib/liveRefresh.js';
	import { formatOffset } from '#lib/offset.js';

	const service = new AlbumService();
	const id = $derived(page.params.id ?? '');

	let album = $state.raw<Album | null>(null);
	let status = $state<'loading' | 'ready' | 'notfound' | 'error'>('loading');

	async function load() {
		try {
			album = await service.get(id);
			status = album ? 'ready' : 'notfound';
		} catch {
			status = 'error';
		}
	}

	onMount(load);

	// Tracks come and go, and audio gets attached; also what was missed while the connection was down
	onMount(() => refreshOnLive(live, ['album.changed'], () => void load()));
</script>

<svelte:head>
	<title>Tune Lad - {album?.title ?? 'Album'}</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<a href={resolve('/albums')} class="text-sm font-medium text-ink-muted hover:text-ink">
		← Albums
	</a>

	{#if status === 'loading'}
		<p class="text-ink-muted" role="status">Loading…</p>
	{:else if status === 'notfound'}
		<h1 class="text-3xl font-bold tracking-tight">Album not found</h1>
	{:else if status === 'error' || !album}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The album could not be loaded.
		</p>
	{:else}
		<div>
			<h1 class="text-3xl font-bold tracking-tight">{album.title}</h1>
			<p class="mt-1 text-ink-muted">
				{album.artist}{#if album.releaseDate}
					· {formatReleaseDate(album.releaseDate)}{/if}
			</p>
		</div>

		{#if (album.tracks ?? []).length === 0}
			<p class="card p-6 text-ink-muted">This album has no tracks yet.</p>
		{:else}
			<ol class="card divide-y divide-line" aria-label="tracks of {album.title}">
				{#each album.tracks ?? [] as track (track.id)}
					<li class="px-4 py-3" data-testid="album-track">
						<div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
							<h2 class="font-medium">
								<span class="text-ink-muted tabular-nums">{track.albumPosition}.</span>
								{track.title}
							</h2>
							{#if track.catalogue}
								<p class="text-sm text-ink-muted">No audio yet</p>
							{:else}
								<a
									class="text-sm font-medium text-accent-ink underline"
									href={resolve(`/tracks?play=${encodeURIComponent(track.id)}`)}
								>
									Play<span class="sr-only"> {track.title}</span>
								</a>
							{/if}
						</div>
						{#if track.performances?.length}
							<ol class="mt-1 space-y-0.5 text-sm" aria-label="tunes of {track.title}">
								{#each track.performances as performance (performance.position)}
									<li>
										{#if performance.tuneId}
											<a
												class="underline hover:text-ink"
												href={resolve('/tunes/[id]', { id: performance.tuneId })}
											>
												{performance.tuneName}
											</a>
											{#if !track.catalogue && performance.startOffsetSeconds > 0}
												<span class="text-ink-muted tabular-nums">
													· {formatOffset(performance.startOffsetSeconds)}
												</span>
											{/if}
										{:else}
											<span>{performance.listedName}</span>
											<span class="text-ink-muted">(not in the tune catalogue)</span>
										{/if}
									</li>
								{/each}
							</ol>
						{/if}
					</li>
				{/each}
			</ol>
		{/if}
	{/if}
</section>
