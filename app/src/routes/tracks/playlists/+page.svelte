<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import EmptyState from '#lib/ui/EmptyState.svelte';
	import PageHeader from '#lib/ui/PageHeader.svelte';
	import { playlists } from '#lib/playlists.svelte.js';
	import { session } from '#lib/session.svelte.js';

	let name = $state('');
	let creating = $state(false);

	async function create(event: SubmitEvent) {
		event.preventDefault();
		if (!name.trim() || creating) return;
		creating = true;
		const created = await playlists.create(name);
		creating = false;
		if (created) {
			name = '';
			await goto(resolve('/tracks/playlists/[id]', { id: created.id }));
		}
	}

	const plural = (n: number) => `${n} ${n === 1 ? 'track' : 'tracks'}`;
</script>

<svelte:head>
	<title>Tune Lad - Playlists</title>
</svelte:head>

<section class="flex flex-col gap-6">
	<PageHeader title="Playlists" subtitle="Your tracks, in the order you want to hear them." />

	{#if !session.loaded}
		<!-- Who is logged in is not known yet -->
	{:else if !session.account}
		<EmptyState title="Log in to build playlists">
			Playlists belong to your account.
			<!-- Served by the server, not the SPA router: it lists the login providers -->
			<a href="/login" rel="external" class="btn-accent mt-4">Log in</a>
		</EmptyState>
	{:else}
		<form class="flex flex-wrap gap-2" onsubmit={create}>
			<label class="sr-only" for="new-playlist-name">Name of the new playlist</label>
			<input
				id="new-playlist-name"
				class="min-w-0 flex-1 rounded-full px-4 py-2 text-sm shadow-sm sm:max-w-sm"
				placeholder="Name your new playlist"
				maxlength="80"
				required
				bind:value={name}
			/>
			<button type="submit" class="btn-accent" disabled={creating}>Create playlist</button>
		</form>

		{#if playlists.loaded && playlists.items.length === 0}
			<EmptyState title="No playlists yet">
				Create one above, then add tracks to it from the
				<a href={resolve('/tracks')} class="font-semibold text-accent-ink hover:underline">
					Tracks page
				</a>.
			</EmptyState>
		{:else}
			<ul class="flex flex-col gap-3" aria-label="your playlists">
				{#each playlists.items as playlist (playlist.id)}
					<li>
						<a
							href={resolve('/tracks/playlists/[id]', { id: playlist.id })}
							class="card flex items-center justify-between gap-4 p-4 transition-shadow hover:shadow-md"
						>
							<span class="min-w-0">
								<span class="block truncate font-semibold">{playlist.name}</span>
								{#if playlist.description}
									<span class="block truncate text-sm text-ink-muted">{playlist.description}</span>
								{/if}
							</span>
							<span class="shrink-0 text-sm text-ink-muted tabular-nums">
								{plural(playlist.trackCount)}
							</span>
						</a>
					</li>
				{/each}
			</ul>
		{/if}
	{/if}
</section>
