<script lang="ts">
	import { resolve } from '$app/paths';
	import { session } from '#lib/session.svelte.js';
	import LogoutButton from './LogoutButton.svelte';
	import Menu from './ui/Menu.svelte';

	let { mobile = false }: { mobile?: boolean } = $props();

	const links = [
		{ path: '/account', label: 'Account' },
		{ path: '/tracks/favourites', label: 'Favourites' },
		{ path: '/tracks/playlists', label: 'Playlists' }
	] as const;

	const initial = $derived(session.account?.displayName.trim().charAt(0).toUpperCase() ?? '');

	const pillClass =
		'text-on-header/80 hover:text-on-header border border-on-header/30 hover:border-on-header px-4 py-1.5 rounded-full text-sm font-medium transition-colors';
	const mobileClass =
		'text-on-header/70 hover:bg-on-header/10 hover:text-on-header block w-full px-3 py-2 rounded-md text-left text-base font-medium';
	const itemClass =
		'flex w-full items-center px-4 py-2 text-left text-sm hover:bg-canvas focus-visible:bg-canvas';
</script>

{#if session.account}
	{#if mobile}
		<div class="space-y-1">
			<p class="px-3 pt-2 text-sm text-on-header/60">{session.account.displayName}</p>
			{#each links as link (link.path)}
				<a href={resolve(link.path)} class={mobileClass}>{link.label}</a>
			{/each}
			<LogoutButton class={mobileClass} />
		</div>
	{:else}
		<Menu
			triggerClass="inline-flex h-9 items-center gap-2 rounded-full pr-2 pl-1 text-sm font-medium text-on-header transition-colors hover:bg-on-header/10"
		>
			{#snippet trigger()}
				<span
					class="flex h-7 w-7 items-center justify-center rounded-full bg-accent text-xs font-bold text-on-accent"
					aria-hidden="true"
				>
					{initial}
				</span>
				<span class="max-w-40 truncate">{session.account?.displayName}</span>
			{/snippet}
			{#each links as link (link.path)}
				<a href={resolve(link.path)} role="menuitem" class={itemClass}>{link.label}</a>
			{/each}
			<div class="my-1 border-t border-line"></div>
			<LogoutButton class={itemClass} role="menuitem" />
		</Menu>
	{/if}
{:else}
	<a href={resolve('/login')} class={mobile ? mobileClass : pillClass}>Log in</a>
{/if}
