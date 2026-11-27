<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { onMount, untrack } from 'svelte';
	import { live } from '#lib/live.svelte.js';
	import { refreshOnLive } from '#lib/liveRefresh.js';
	import Icon from '#lib/Icon.svelte';
	import PlaylistTracks from '#lib/PlaylistTracks.svelte';
	import EmptyState from '#lib/ui/EmptyState.svelte';
	import PageHeader from '#lib/ui/PageHeader.svelte';
	import { toast } from '#lib/ui/toast.svelte.js';
	import { player } from '#lib/player.svelte.js';
	import { PlaylistError, playlists, type Playlist } from '#lib/playlists.svelte.js';
	import { session } from '#lib/session.svelte.js';
	import type { Track } from '../../Tracks.js';

	const id = $derived(page.params.id ?? '');
	const service = playlists.service;

	let playlist = $state.raw<Playlist | null>(null);
	let status = $state<'loading' | 'ready' | 'notfound' | 'error'>('loading');

	let renaming = $state(false);
	let nameDraft = $state('');
	let descriptionDraft = $state('');

	async function load() {
		try {
			playlist = await service.get(id);
			status = 'ready';
		} catch (e) {
			if (e instanceof PlaylistError && e.status === 404) {
				playlist = null;
				status = 'notfound';
			} else if (status !== 'ready') {
				status = 'error';
			}
		}
	}

	// Loads the playlist once it is known who is logged in, and again when another playlist is opened
	$effect(() => {
		void id;
		if (session.loaded && session.account) untrack(() => void load());
	});

	// Another tab changed this playlist (or one the list shows): fetch it again, unless the user is editing its name
	onMount(() =>
		refreshOnLive(live, ['playlist.changed'], () => {
			if (session.account && !renaming) void load();
		})
	);

	function startRename() {
		if (!playlist) return;
		nameDraft = playlist.name;
		descriptionDraft = playlist.description ?? '';
		renaming = true;
	}

	async function saveRename(event: SubmitEvent) {
		event.preventDefault();
		if (!playlist || !nameDraft.trim()) return;
		try {
			const renamed = await service.rename(id, nameDraft.trim(), descriptionDraft.trim());
			playlist = { ...playlist, name: renamed.name, description: renamed.description };
			renaming = false;
			void playlists.load();
		} catch (e) {
			toast.error(e instanceof PlaylistError ? e.message : 'Could not rename the playlist.');
		}
	}

	async function deletePlaylist() {
		if (
			!playlist ||
			!window.confirm(`Delete the playlist "${playlist.name}"? Its tracks are kept.`)
		)
			return;
		try {
			await service.delete(id);
			void playlists.load();
			await goto(resolve('/tracks/playlists'));
		} catch (e) {
			toast.error(e instanceof PlaylistError ? e.message : 'Could not delete the playlist.');
		}
	}

	/** Shows the new order at once and saves it; goes back to the previous order, and says so, if that fails. */
	async function reorder(trackIds: string[]) {
		if (!playlist) return;
		const previous = playlist;
		const byId = new Map(previous.tracks.map((t) => [t.id, t]));
		playlist = { ...previous, tracks: trackIds.map((trackId) => byId.get(trackId)!) };
		try {
			await service.reorder(id, trackIds);
			void playlists.load();
		} catch (e) {
			playlist = previous;
			toast.error(
				e instanceof PlaylistError && e.status === 409
					? e.message
					: 'Could not save the new order. It is back as it was.'
			);
		}
	}

	async function removeTrack(track: Track) {
		if (!playlist) return;
		const previous = playlist;
		playlist = {
			...previous,
			trackCount: previous.trackCount - 1,
			tracks: previous.tracks.filter((t) => t.id !== track.id)
		};
		try {
			await service.removeTrack(id, track.id);
			void playlists.load();
		} catch {
			playlist = previous;
			toast.error(`Could not remove “${track.title}” from the playlist. Try again.`);
		}
	}

	function play(index: number) {
		if (playlist) player.playFrom(playlist.tracks, index);
	}

	const plural = (n: number) => `${n} ${n === 1 ? 'track' : 'tracks'}`;
</script>

<svelte:head>
	<title>Tune Lad - {playlist?.name ?? 'Playlist'}</title>
</svelte:head>

<section class="flex flex-col gap-6">
	{#if !session.loaded}
		<!-- Who is logged in is not known yet -->
	{:else if !session.account}
		<EmptyState title="Log in to see this playlist">
			Playlists belong to their owner.
			<!-- Served by the server, not the SPA router: it lists the login providers -->
			<a href="/login" rel="external" class="btn-accent mt-4">Log in</a>
		</EmptyState>
	{:else if status === 'notfound'}
		<EmptyState title="Playlist not found">
			It may have been deleted.
			<a href={resolve('/tracks/playlists')} class="btn-accent mt-4">Your playlists</a>
		</EmptyState>
	{:else if status === 'error'}
		<EmptyState title="The playlist could not be loaded">
			Check your connection and try again.
			<button type="button" class="btn-accent mt-4" onclick={load}>Try again</button>
		</EmptyState>
	{:else if playlist}
		<a
			href={resolve('/tracks/playlists')}
			class="text-sm font-medium text-ink-muted hover:text-ink"
		>
			Playlists
		</a>

		{#if renaming}
			<form class="card flex flex-col gap-3 p-4" aria-label="rename playlist" onsubmit={saveRename}>
				<label class="block text-sm font-medium">
					Name
					<input
						class="mt-1 block w-full rounded-md text-sm"
						required
						maxlength="80"
						bind:value={nameDraft}
					/>
				</label>
				<label class="block text-sm font-medium">
					Description
					<textarea
						class="mt-1 block w-full rounded-md text-sm"
						maxlength="500"
						bind:value={descriptionDraft}></textarea>
				</label>
				<div class="flex gap-2">
					<button type="submit" class="btn-accent">Save</button>
					<button type="button" class="btn-quiet" onclick={() => (renaming = false)}>Cancel</button>
				</div>
			</form>
		{:else}
			<PageHeader
				title={playlist.name}
				subtitle={[playlist.description, plural(playlist.tracks.length)]
					.filter(Boolean)
					.join(' · ')}
			>
				{#snippet actions()}
					<button
						type="button"
						class="btn-accent"
						disabled={playlist?.tracks.length === 0}
						onclick={() => play(0)}
					>
						<Icon name="play" class="h-4 w-4" />
						Play
					</button>
					<button type="button" class="btn-quiet" onclick={startRename}>
						<Icon name="edit" class="h-4 w-4" />
						Rename
					</button>
					<button
						type="button"
						class="btn-quiet text-danger"
						onclick={deletePlaylist}
						aria-label="delete playlist {playlist?.name}"
					>
						<Icon name="trash" class="h-4 w-4" />
						Delete
					</button>
				{/snippet}
			</PageHeader>
		{/if}

		{#if playlist.tracks.length === 0}
			<EmptyState title="This playlist is empty">
				Add tracks from the
				<a href={resolve('/tracks')} class="font-semibold text-accent-ink hover:underline">
					Tracks page
				</a>
				with the “Add to playlist” button.
			</EmptyState>
		{:else}
			<PlaylistTracks
				tracks={playlist.tracks}
				onreorder={reorder}
				onremove={removeTrack}
				onplay={play}
			/>
		{/if}
	{/if}
</section>
