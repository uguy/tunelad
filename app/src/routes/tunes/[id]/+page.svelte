<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import AbcViewer from '#lib/AbcViewer.svelte';
	import Comments from '#lib/Comments.svelte';
	import IdentificationBadge from '../../tracks/IdentificationBadge.svelte';
	import { formatOffset } from '#lib/offset.js';
	import TuneService, { type Tune, type TunePerformance } from '../Tunes.js';

	const service = new TuneService();
	const id = $derived(page.params.id ?? '');

	let tune = $state.raw<Tune | null>(null);
	let performances = $state.raw<TunePerformance[]>([]);
	/** Only Performances with a Transcription draw a sheet. */
	const sheets = $derived(performances.filter((p) => p.abc?.trim()));
	let status = $state<'loading' | 'ready' | 'notfound' | 'error'>('loading');

	onMount(async () => {
		try {
			[tune, performances] = await Promise.all([service.findOne(id), service.performances(id)]);
			status = tune ? 'ready' : 'notfound';
		} catch {
			status = 'error';
		}
	});
</script>

<svelte:head>
	<title>Tune Lad - {tune?.name ?? 'Tune'}</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-3xl flex-col gap-6">
	<a href={resolve('/tunes')} class="text-sm font-medium text-ink-muted hover:text-ink">← Tunes</a>

	{#if status === 'loading'}
		<p class="text-ink-muted" role="status">Loading…</p>
	{:else if status === 'notfound'}
		<h1 class="text-3xl font-bold tracking-tight">Tune not found</h1>
	{:else if status === 'error' || !tune}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The tune could not be loaded.
		</p>
	{:else}
		<div>
			<h1 class="text-3xl font-bold tracking-tight">{tune.name}</h1>
			<dl class="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink-muted">
				<div class="flex gap-1">
					<dt class="sr-only">Dance type</dt>
					<dd class="capitalize">{tune.danceType}</dd>
				</div>
				{#if tune.key}<div class="flex gap-1">
						<dt>Key</dt>
						<dd class="text-ink">{tune.key}</dd>
					</div>{/if}
				{#if tune.author}<div class="flex gap-1">
						<dt>Author</dt>
						<dd class="text-ink">{tune.author}</dd>
					</div>{/if}
				{#if tune.aliases.length > 0}
					<div class="flex gap-1">
						<dt>Also known as</dt>
						<dd class="text-ink">{tune.aliases.join(', ')}</dd>
					</div>
				{/if}
				{#if tune.sourceId}
					<div>
						<a
							class="underline hover:text-ink"
							href="https://thesession.org/tunes/{tune.sourceId}"
							rel="external noopener"
							target="_blank"
						>
							On thesession.org<span class="sr-only"> (opens in a new tab)</span>
						</a>
					</div>
				{/if}
			</dl>
		</div>
		{#if sheets.length > 0}
			{#each sheets as performance (`${performance.trackId}-${performance.position}`)}
				<section class="flex flex-col gap-2">
					<h2 class="text-lg font-semibold">
						{performance.trackTitle}{#if performance.artist}<span
								class="font-normal text-ink-muted"
							>
								by {performance.artist}
							</span>{/if}
					</h2>
					<p class="text-sm text-ink-muted">
						{#if performance.key}Key {performance.key} ·
						{/if}<a
							class="underline hover:text-ink"
							href={resolve(
								`/tracks?play=${encodeURIComponent(performance.trackId)}&t=${performance.startOffsetSeconds}`
							)}
						>
							Play from {formatOffset(performance.startOffsetSeconds)}<span class="sr-only">
								in {performance.trackTitle}
							</span>
						</a>
						<IdentificationBadge state={performance.identification} />
					</p>
					{#if performance.notes}
						<p class="text-sm whitespace-pre-line text-ink-muted">{performance.notes}</p>
					{/if}
					<AbcViewer
						abc={performance.abc}
						title="{tune.name} as played in {performance.trackTitle}"
					/>
				</section>
			{/each}
		{:else}
			<h2 class="sr-only">Reference transcription</h2>
			<AbcViewer abc={tune.abc} title={tune.name} />
		{/if}
		<Comments subject="tunes" {id} />
	{/if}
</section>
