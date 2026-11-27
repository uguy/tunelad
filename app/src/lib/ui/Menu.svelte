<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from '../Icon.svelte';

	let {
		label,
		trigger,
		children,
		align = 'right',
		triggerClass = ''
	}: {
		/** The accessible name of the button, when `trigger` is only an icon. */
		label?: string;
		/** What the button shows. */
		trigger: Snippet;
		/** The menu's items: elements with role menuitem, menuitemradio or menuitemcheckbox. */
		children: Snippet;
		align?: 'left' | 'right';
		triggerClass?: string;
	} = $props();

	let open = $state(false);
	let root: HTMLElement | undefined = $state();
	let button: HTMLButtonElement | undefined = $state();

	const itemSelector = '[role^="menuitem"]:not(:disabled)';

	function items(): HTMLElement[] {
		return root ? [...root.querySelectorAll<HTMLElement>(itemSelector)] : [];
	}

	function show() {
		open = true;
		// Once the panel is in the page: the first item gets the focus, as the keyboard user expects
		queueMicrotask(() => items()[0]?.focus());
	}

	function close(restoreFocus = false) {
		open = false;
		if (restoreFocus) button?.focus();
	}

	function onButtonKeydown(event: KeyboardEvent) {
		if (event.key === 'ArrowDown' && !open) {
			event.preventDefault();
			show();
		}
	}

	function onPanelKeydown(event: KeyboardEvent) {
		const list = items();
		const at = list.indexOf(document.activeElement as HTMLElement);
		if (event.key === 'ArrowDown') {
			event.preventDefault();
			list[(at + 1) % list.length]?.focus();
		} else if (event.key === 'ArrowUp') {
			event.preventDefault();
			list[(at - 1 + list.length) % list.length]?.focus();
		} else if (event.key === 'Home') {
			event.preventDefault();
			list[0]?.focus();
		} else if (event.key === 'End') {
			event.preventDefault();
			list.at(-1)?.focus();
		} else if (event.key === 'Tab') {
			close();
		}
	}

	function onWindowKeydown(event: KeyboardEvent) {
		if (open && event.key === 'Escape') close(true);
	}

	function onWindowPointerdown(event: PointerEvent) {
		if (open && root && !root.contains(event.target as Node)) close();
	}
</script>

<svelte:window onkeydown={onWindowKeydown} onpointerdown={onWindowPointerdown} />

<div class="relative" bind:this={root}>
	<button
		bind:this={button}
		type="button"
		class={triggerClass}
		aria-label={label}
		aria-haspopup="menu"
		aria-expanded={open}
		onclick={() => (open ? close() : show())}
		onkeydown={onButtonKeydown}
	>
		{@render trigger()}
		<Icon
			name="chevron"
			class="h-4 w-4 opacity-70 transition-transform {open ? 'rotate-180' : ''}"
		/>
	</button>

	{#if open}
		<!-- Choosing an item closes the menu: a click on an item bubbles here. The close waits a tick so that the
		     click's own effect (a link followed, a form submitted) happens while the item is still in the page. -->
		<div
			role="menu"
			tabindex="-1"
			class="absolute top-full z-50 mt-2 min-w-52 overflow-hidden rounded-md bg-surface py-1 text-ink shadow-lg ring-1 ring-line {align ===
			'right'
				? 'right-0'
				: 'left-0'}"
			onkeydown={onPanelKeydown}
			onclick={(event) => {
				if ((event.target as HTMLElement).closest('[role^="menuitem"]')) setTimeout(() => close());
			}}
		>
			{@render children()}
		</div>
	{/if}
</div>
