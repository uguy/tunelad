<script lang="ts">
	import Icon from './Icon.svelte';
	import Menu from './ui/Menu.svelte';
	import { theme, type ThemePreference } from './theme.svelte.js';
	import type { IconName } from './icons.js';

	let { mobile = false }: { mobile?: boolean } = $props();

	const choices: { value: ThemePreference; label: string; icon: IconName }[] = [
		{ value: 'system', label: 'System', icon: 'system' },
		{ value: 'light', label: 'Light', icon: 'sun' },
		{ value: 'dark', label: 'Dark', icon: 'moon' }
	];

	const current = $derived(choices.find((c) => c.value === theme.preference) ?? choices[0]);
</script>

{#if mobile}
	<div role="group" aria-label="Theme" class="flex gap-1 px-3 py-2">
		{#each choices as choice (choice.value)}
			<button
				type="button"
				aria-pressed={theme.preference === choice.value}
				class="flex flex-1 items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors {theme.preference ===
				choice.value
					? 'bg-on-header/15 text-on-header'
					: 'text-on-header/70 hover:bg-on-header/10 hover:text-on-header'}"
				onclick={() => theme.choose(choice.value)}
			>
				<Icon name={choice.icon} class="h-4 w-4" />
				{choice.label}
			</button>
		{/each}
	</div>
{:else}
	<Menu
		label="Theme: {current.label}"
		triggerClass="inline-flex h-9 items-center gap-1 rounded-full px-2.5 text-on-header/80 transition-colors hover:bg-on-header/10 hover:text-on-header"
	>
		{#snippet trigger()}
			<Icon name={theme.resolved === 'dark' ? 'moon' : 'sun'} class="h-5 w-5" />
		{/snippet}
		{#each choices as choice (choice.value)}
			<button
				type="button"
				role="menuitemradio"
				aria-checked={theme.preference === choice.value}
				class="flex w-full items-center gap-3 px-4 py-2 text-left text-sm hover:bg-canvas focus-visible:bg-canvas"
				onclick={() => theme.choose(choice.value)}
			>
				<Icon name={choice.icon} class="h-4 w-4 text-ink-muted" />
				<span class="flex-1">{choice.label}</span>
				{#if theme.preference === choice.value}
					<Icon name="check" class="h-4 w-4 text-accent-ink" />
				{/if}
			</button>
		{/each}
	</Menu>
{/if}
