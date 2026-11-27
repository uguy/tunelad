<script lang="ts">
	import { onMount } from 'svelte';
	import { moveItem } from '#lib/playlistOrder.js';
	import TuneService, { type Tune } from '../tunes/Tunes.js';
	import { MAX_TUNES } from './Sets.js';

	interface Props {
		/** The tunes of the set being built, in order; a tune may be added twice. */
		chosen?: Tune[];
		/** Heading level of the two panels' titles. */
		headingLevel?: 2 | 3;
	}

	let { chosen = $bindable([]), headingLevel = 2 }: Props = $props();

	const tuneService = new TuneService();

	let text = $state('');
	let found = $state.raw<Tune[]>([]);
	/** What assistive technology is told after each change. */
	let announcement = $state('');

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

	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
	const iconButton =
		'inline-flex size-9 items-center justify-center rounded-full border border-line text-sm hover:border-ink-muted disabled:opacity-40';
</script>

<div class="grid gap-6 md:grid-cols-2">
	<div class="card space-y-3 p-4">
		<svelte:element this={`h${headingLevel}`} class="text-lg font-semibold">
			Find tunes
		</svelte:element>
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
		<svelte:element this={`h${headingLevel}`} class="text-lg font-semibold">The set</svelte:element>
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
	</div>
</div>

<p class="sr-only" role="status" aria-live="polite">{announcement}</p>
