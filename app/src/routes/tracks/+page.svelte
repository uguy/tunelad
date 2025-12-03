<script lang="ts">
	import TrackList from './component/TrackList.svelte';
	import playerSource from '$lib/store/media-source.js';
	import { resolve } from '$app/paths';

	import TrackService from './Tracks.js';

	/** @type {import('./$types').PageData} */
	export let data;

	const trackService = new TrackService(fetch);

	async function refreshTracks() {
		const q = (document.querySelector('#search-input') as HTMLInputElement).value;
		data.tracks = await trackService.findAll(q);
	}
</script>

<svelte:head>
	<title>Tune Lad - Tracks</title>
</svelte:head>

<section class="flex flex-col w-full space-y-5">
	<h1 class="text-3xl leading-2">Tracks</h1>

	<div class="flex flex-row pY-10">
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
	</div>
	<br />
	<div class="flex flex-row pY-10">
		<form class="w-full h-full">
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
					on:keydown={(e) => {
						if (e.key === 'Enter') {
							refreshTracks();
						}
					}}
				/>
				<button
					id="search-track-btn"
					aria-label="search-track-btn"
					type="button"
					class="bg-gray-300 absolute end-1.5 bottom-1.5 bg-brand hover:bg-brand-strong box-border border border-transparent shadow-xs font-medium leading-5 rounded text-xs px-3 py-1 focus:outline-none"
					on:click|preventDefault={() => {
						refreshTracks();
					}}
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
		tracks={data.tracks}
		on:trackPlayButtonClicked={(event) =>
			($playerSource = `api/tracks/${event.detail.trackId}/play`)}
		on:trackTagClicked={(event) => {
			document.querySelector('#search-input').value = event.detail.tag;
			refreshTracks();
		}}
	/>
</section>
