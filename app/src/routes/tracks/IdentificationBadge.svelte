<script lang="ts">
	import type { IdentificationState } from './Tracks.js';

	interface Props {
		/** Where the performance stands in identification; nothing is drawn without one. */
		state?: IdentificationState;
	}

	let { state }: Props = $props();

	const labels: Record<IdentificationState, string> = {
		pending: 'Not identified',
		identified: 'Identified from the audio',
		nothing_found: 'Nothing found in the audio',
		corrected: 'Corrected'
	};
</script>

{#if state}
	<span
		data-identification={state}
		class="rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap {state === 'identified'
			? 'bg-accent-soft text-accent-ink'
			: 'bg-canvas text-ink-muted'}"
	>
		{labels[state]}
	</span>
{/if}
