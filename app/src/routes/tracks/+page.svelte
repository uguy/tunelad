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

<section class="flex flex-col w-full space-y-5">
	<h1 class="text-3xl leading-2">Tracks</h1>

	<div class="flex flex-row">
		<div class="w-full">
			<a
				href={resolve('/tracks/favourites')}
				class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium">Favourites</a
			>
			<a
				href={resolve('/tracks/playlists')}
				class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium">Playlists</a
			>
			<a
				href={resolve('/tracks/albums')}
				class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium">Albums</a
			>
		</div>
		<div class="shrink-0">
			{#if session.account}
				<a
					href={resolve('/tracks/upload')}
					class="bg-gray-900 text-white px-3 py-2 rounded-md text-sm font-medium">Upload</a
				>
			{:else}
				<!-- Served by the server, not the SPA router: it lists the login providers -->
				<a href="/login" rel="external" class="underline text-sm font-medium">Log in to upload</a>
			{/if}
		</div>
	</div>
	<br />
	<div class="flex flex-row">
		<form
			class="w-full h-full"
			onsubmit={(event) => {
				event.preventDefault();
				refreshTracks();
			}}
		>
			<label for="search-input" class="block mb-2.5 text-sm font-medium text-heading sr-only"
				>Search</label
			>
			<div class="relative">
				<div class="absolute inset-y-0 start-0 flex items-center ps-3 pointer-events-none">
					<svg
						class="w-4 h-4 text-body"
						aria-hidden="true"
						xmlns="http://www.w3.org/2000/svg"
						width="24"
						height="24"
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
					class="block w-full p-3 ps-9 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base shadow-xs placeholder:text-body"
					type="search"
					placeholder="search for tracks ..."
					bind:value={query}
				/>
				<button
					id="search-track-btn"
					aria-label="search-track-btn"
					type="submit"
					class="bg-gray-300 absolute end-1.5 bottom-1.5 box-border border border-transparent shadow-xs font-medium leading-5 rounded text-xs px-3 py-1 focus:outline-none"
				>
					<svg
						xmlns="http://www.w3.org/2000/svg"
						viewBox="0 0 24 24"
						fill="currentColor"
						class="w-6 h-6 ml-auto mr-auto"
					>
						<path
							fill-rule="evenodd"
							d="M10.5 3.75a6.75 6.75 0 100 13.5 6.75 6.75 0 000-13.5zM2.25 10.5a8.25 8.25 0 1114.59 5.28l4.69 4.69a.75.75 0 11-1.06 1.06l-4.69-4.69A8.25 8.25 0 012.25 10.5z"
							clip-rule="evenodd"
						/>
					</svg>
				</button>
			</div>
		</form>
	</div>

	<TrackList
		{tracks}
		onplay={(trackId) =>
			player.playFrom(
				tracks,
				tracks.findIndex((t) => t.id === trackId)
			)}
		onupdated={replaceTrack}
		ondeleted={removeTrack}
		ontagclick={searchTag}
	/>
</section>
