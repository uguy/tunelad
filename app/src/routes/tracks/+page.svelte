<script>
	import * as api from '$lib/api.js';

	import TrackList from './component/TrackList.svelte';
	import Player from './component/Player.svelte';

	/** @type {import('./$types').PageData} */
	export let data;

	let playerSrc;

	async function refreshTracks() {
		const q = document.querySelector('#search-input').value;
		data.tracks = await api.get(fetch, 'search/tracks?q=' + q);
	}
</script>

<svelte:head>
	<title>Tune Lad - Tracks</title>
</svelte:head>

<div class="flex flex-col w-full space-y-5">
	<h1 class="text-3xl leading-2">Tracks</h1>
	<br />
	<div class="flex flex-row pY-10">
		<div class="basis-5/6 w-full">
			<input
				id="search-input"
				class="block w-full rounded-md border-gray-300"
				type="text"
				placeholder="search for tracks"
			/>
		</div>
		<div class="flex-none basis-1/6">
			<button
				id="search-track-btn"
				aria-label="search-track-btn"
				type="button"
				class="block w-full h-full bg-gray-300 rounded-md"
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
	</div>
	<hr />

	<TrackList
		tracks={data.tracks}
		on:trackPlayButtonClicked={(event) => (playerSrc = `api/tracks/${event.detail.trackId}/play`)}
		on:trackTagClicked={(event) => {
			document.querySelector('#search-input').value = event.detail.tag;
			refreshTracks();
		}}
	/>
	<div class="flex flex-row pY-10">
		<Player src={playerSrc} />
	</div>
</div>
