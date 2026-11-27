<script lang="ts">
	import IdentificationBadge from './IdentificationBadge.svelte';
	import { MAX_ABC, MAX_KEY, MAX_NOTES, type PerformanceForm } from './Performances.js';

	interface Props {
		/** The names of the tunes, in order: one group of fields each. */
		names: string[];
		/** One form per tune. */
		values: PerformanceForm[];
	}

	let { names, values = $bindable() }: Props = $props();

	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
</script>

{#each names as name, i (i)}
	{#if values[i]}
		<fieldset class="space-y-3 rounded-md border border-line p-3">
			<legend class="px-1 text-sm font-semibold">
				<span class="text-ink-muted tabular-nums">{i + 1}.</span>
				{name}
				<IdentificationBadge state={values[i].identification} />
			</legend>
			<div class="grid gap-3 sm:grid-cols-2">
				<label class="block text-sm font-medium">
					Starts at <span class="font-normal text-ink-muted">(m:ss)</span>
					<input
						class={inputClass}
						inputmode="decimal"
						autocomplete="off"
						placeholder="0:00"
						bind:value={values[i].start}
					/>
				</label>
				<label class="block text-sm font-medium">
					Key
					<input
						class={inputClass}
						maxlength={MAX_KEY}
						autocomplete="off"
						placeholder="Em"
						bind:value={values[i].key}
					/>
				</label>
			</div>
			<label class="block text-sm font-medium">
				Notes
				<textarea class={inputClass} rows="2" maxlength={MAX_NOTES} bind:value={values[i].notes}
				></textarea>
			</label>
			<label class="block text-sm font-medium">
				Transcription <span class="font-normal text-ink-muted">(ABC notation, optional)</span>
				<textarea
					class="{inputClass} font-mono"
					rows="4"
					maxlength={MAX_ABC}
					spellcheck="false"
					autocapitalize="off"
					bind:value={values[i].abc}></textarea>
			</label>
		</fieldset>
	{/if}
{/each}
