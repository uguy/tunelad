<script lang="ts">
	import Icon from '../Icon.svelte';
	import { toast } from './toast.svelte.js';
</script>

<!-- A live region: screen readers announce a message when it appears. Above the player bar. It has no role of
     its own: pages already have status and alert elements that tests and users look for by role. -->
<div
	class="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4"
	aria-live="polite"
>
	{#each toast.messages as message (message.id)}
		<div
			class="pointer-events-auto flex max-w-md items-center gap-3 rounded-full px-4 py-2 text-sm shadow-lg ring-1 {message.kind ===
			'error'
				? 'bg-danger-soft text-danger ring-danger-line'
				: 'bg-header text-on-header ring-line'}"
		>
			<span>{message.text}</span>
			<button
				type="button"
				class="-mr-2 rounded-full p-1 opacity-70 hover:opacity-100"
				aria-label="Dismiss"
				onclick={() => toast.dismiss(message.id)}
			>
				<Icon name="close" class="h-4 w-4" />
			</button>
		</div>
	{/each}
</div>
