<script lang="ts">
	import { resolve } from '$app/paths';
	import LogoutButton from '#lib/LogoutButton.svelte';
	import EmptyState from '#lib/ui/EmptyState.svelte';
	import PageHeader from '#lib/ui/PageHeader.svelte';
	import { favourites } from '#lib/favourites.svelte.js';
	import { playlists } from '#lib/playlists.svelte.js';
	import { session } from '#lib/session.svelte.js';

	const account = $derived(session.account);
	const initial = $derived(account?.displayName.trim().charAt(0).toUpperCase() ?? '');

	const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
</script>

<svelte:head>
	<title>Tune Lad - Account</title>
</svelte:head>

<section class="flex flex-col gap-6">
	<PageHeader title="Account" />

	{#if !session.loaded}
		<!-- Who is logged in is not known yet -->
	{:else if !account}
		<EmptyState title="Log in to see your account">
			Your account holds your uploads, favourites and playlists.
			<!-- Served by the server, not the SPA router: it lists the login providers -->
			<a href="/login" rel="external" class="btn-accent mt-4">Log in</a>
		</EmptyState>
	{:else}
		<div class="card flex flex-wrap items-center gap-4 p-6">
			<span
				class="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-accent text-2xl font-bold text-on-accent"
				aria-hidden="true"
			>
				{initial}
			</span>
			<div class="min-w-0 flex-1">
				<p class="truncate text-xl font-semibold" data-testid="account-name">
					{account.displayName}
				</p>
				<p class="text-ink-muted" data-testid="account-role">
					{account.admin ? 'Administrator' : 'Member'}
				</p>
			</div>
			<LogoutButton />
		</div>

		<ul class="grid gap-3 sm:grid-cols-2" aria-label="your collections">
			<li>
				<a
					href={resolve('/tracks/favourites')}
					class="card flex h-full flex-col gap-1 p-5 transition-shadow hover:shadow-md"
				>
					<span class="text-2xl font-bold tabular-nums">
						{favourites.loaded ? plural(favourites.tracks.length, 'favourite', 'favourites') : '…'}
					</span>
					<span class="text-sm text-ink-muted">Tracks you marked with the heart</span>
				</a>
			</li>
			<li>
				<a
					href={resolve('/tracks/playlists')}
					class="card flex h-full flex-col gap-1 p-5 transition-shadow hover:shadow-md"
				>
					<span class="text-2xl font-bold tabular-nums">
						{playlists.loaded ? plural(playlists.items.length, 'playlist', 'playlists') : '…'}
					</span>
					<span class="text-sm text-ink-muted">Your tracks, in your order</span>
				</a>
			</li>
		</ul>
	{/if}
</section>
