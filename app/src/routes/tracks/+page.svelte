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
				<i class="fa-solid fa-search text-1xl" />
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
