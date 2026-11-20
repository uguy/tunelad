<script lang="ts">
	import TrackList from './component/TrackList.svelte';
	import { player } from '#lib/player.svelte.js';
	import { session } from '#lib/session.svelte.js';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { onMount } from 'svelte';

	import TrackService, { type Track } from './Tracks.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const trackService = new TrackService();
	let query = $state('');
	// The loaded tracks, replaced locally by searches and by edits the search has not caught up with yet. A track
	// just uploaded comes first, until search finds it: the upload page hands it over in the page state.
	let tracks = $derived(withUploaded(data.tracks, page.state.uploaded));

	function withUploaded(list: Track[], uploaded: Track | undefined): Track[] {
		return uploaded && !list.some((t) => t.id === uploaded.id) ? [uploaded, ...list] : list;
	}

	/** Seconds to start at from a `t` query value; anything not a non-negative number starts at 0. */
	function startTime(t: string | null): number {
		const seconds = Number(t);
		return t !== null && Number.isFinite(seconds) && seconds > 0 ? seconds : 0;
	}

	// Deep link: /tracks?play=<id>&t=<seconds> loads the player on that track at that time.
	// Read in the browser only, since the page is prerendered without query parameters.
	onMount(() => {
		const trackId = page.url.searchParams.get('play');
		if (trackId) {
			const listed = tracks.find((t) => t.id === trackId) ?? null;
			player.play(trackId, startTime(page.url.searchParams.get('t')), listed);
			// A track that is not listed is fetched, for the player bar to show its title
			if (!listed) {
				trackService.findOne(trackId).then((track) => {
					if (track && player.isLoaded(track.id)) player.track = track;
				});
			}
		}
	});

	async function refreshTracks() {
		tracks = await trackService.findAll(query);
	}

	function replaceTrack(track: Track) {
		// Search catches up a few seconds later: show the server's answer right away
		tracks = tracks.map((t) => (t.id === track.id ? { ...t, ...track } : t));
	}

	function removeTrack(trackId: string) {
		tracks = tracks.filter((t) => t.id !== trackId);
	}

	function searchTag(tag: string) {
		query = tag;
		refreshTracks();
	}
</script>

<svelte:head>
	<title>Tune Lad - Tracks</title>
</svelte:head>

<section class="flex w-full flex-col gap-6">
	<div class="flex flex-wrap items-end justify-between gap-4">
		<div>
			<h1 class="text-3xl font-bold tracking-tight">Tracks</h1>
			<nav aria-label="collections" class="mt-3 flex gap-1 text-sm font-medium">
				{#each [{ path: '/tracks/favourites', label: 'Favourites' }, { path: '/tracks/playlists', label: 'Playlists' }, { path: '/tracks/albums', label: 'Albums' }] as const as link (link.path)}
					<a
						href={resolve(link.path)}
						class="rounded-full px-3 py-1 text-ink-muted hover:bg-surface hover:text-ink"
						>{link.label}</a
					>
				{/each}
			</nav>
		</div>
		<div class="shrink-0">
			{#if session.account}
				<a href={resolve('/tracks/upload')} class="btn-accent">Upload</a>
			{:else}
				<!-- Served by the server, not the SPA router: it lists the login providers -->
				<a href="/login" rel="external" class="btn-quiet">Log in to upload</a>
			{/if}
		</div>
	</div>

	<form
		role="search"
		onsubmit={(event) => {
			event.preventDefault();
			refreshTracks();
		}}
	>
		<label for="search-input" class="sr-only">Search</label>
		<div class="relative">
			<div class="pointer-events-none absolute inset-y-0 start-0 flex items-center ps-4">
				<svg
					class="h-4 w-4 text-ink-muted"
					aria-hidden="true"
					xmlns="http://www.w3.org/2000/svg"
					fill="none"
					viewBox="0 0 24 24"
					><path
						stroke="currentColor"
						stroke-linecap="round"
						stroke-width="2"
						d="m21 21-3.5-3.5M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z"
					/></svg
				>
			</div>
			<input
				id="search-input"
				class="block w-full rounded-full border-line bg-surface py-2.5 ps-10 pe-24 text-sm shadow-sm placeholder:text-ink-muted focus:border-accent focus:ring-accent"
				type="search"
				placeholder="search for tracks ..."
				bind:value={query}
			/>
			<button
				id="search-track-btn"
				aria-label="search-track-btn"
				type="submit"
				class="btn-accent absolute end-1 top-1/2 -translate-y-1/2 px-4 py-1.5 text-xs"
				>Search</button
			>
		</div>
	</form>

	<TrackList {tracks} onupdated={replaceTrack} ondeleted={removeTrack} ontagclick={searchTag} />
</section>
