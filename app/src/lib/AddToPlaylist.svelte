<script lang="ts">
	import Icon from './Icon.svelte';
	import Menu from './ui/Menu.svelte';
	import { playlists } from './playlists.svelte.js';
	import { session } from './session.svelte.js';

	let { trackId, title }: { trackId: string; title: string } = $props();

	let creating = $state(false);
	let name = $state('');

	async function createAndAdd(event: SubmitEvent) {
		event.preventDefault();
		if (!name.trim()) return;
		const created = await playlists.create(name);
		if (created) {
			await playlists.addTrack(created, { id: trackId, title });
			name = '';
			creating = false;
		}
	}
</script>

<!-- Only for a logged-in user: playlists belong to an account -->
{#if session.account}
	<Menu
		label="add track {title} to a playlist button"
		onopen={() => {
			creating = false;
			void playlists.load();
		}}
		triggerClass="inline-flex h-9 items-center rounded-full px-2 text-ink-muted transition-colors hover:bg-canvas hover:text-ink"
	>
		{#snippet trigger()}
			<Icon name="playlistAdd" />
		{/snippet}

		<p class="px-4 pt-2 pb-1 text-xs text-ink-muted">Add to playlist</p>
		{#each playlists.items as playlist (playlist.id)}
			<button
				type="button"
				role="menuitem"
				class="flex w-full items-center justify-between gap-4 px-4 py-2 text-left text-sm hover:bg-canvas focus-visible:bg-canvas"
				onclick={() => playlists.addTrack(playlist, { id: trackId, title })}
			>
				<span class="truncate">{playlist.name}</span>
				<span class="text-xs text-ink-muted tabular-nums">{playlist.trackCount}</span>
			</button>
		{:else}
			<p class="px-4 py-2 text-sm text-ink-muted">You have no playlist yet.</p>
		{/each}

		<div class="my-1 border-t border-line"></div>
		{#if creating}
			<form class="flex gap-2 px-3 py-2" onsubmit={createAndAdd}>
				<label class="sr-only" for="new-playlist-{trackId}">Name of the new playlist</label>
				<!-- svelte-ignore a11y_autofocus -->
				<input
					id="new-playlist-{trackId}"
					class="min-w-0 flex-1 rounded-md px-2 py-1 text-sm"
					placeholder="Playlist name"
					maxlength="80"
					required
					autofocus
					bind:value={name}
				/>
				<button type="submit" class="btn-accent px-3 py-1 text-xs">Create</button>
			</form>
		{:else}
			<button
				type="button"
				role="menuitem"
				data-keep-open
				class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-accent-ink hover:bg-canvas focus-visible:bg-canvas"
				onclick={() => (creating = true)}
			>
				<Icon name="add" class="h-4 w-4" />
				New playlist
			</button>
		{/if}
	</Menu>
{/if}
