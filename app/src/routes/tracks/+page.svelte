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

<div class="row">
	<div class="col-md-3" />
	<div class="col-md-6">
		<h1>Tracks</h1>
		<br />
		<div class="row">
			<div class="col-sm-10">
				<input id="search-input" class="form-control" type="text" placeholder="search for tracks" />
			</div>
			<div class="col-sm-2">
				<button
					id="search-track-btn"
                    aria-label="search-track-btn"
                    type="button"
					class="btn btn-outline-primary"
					on:click|preventDefault={() => {
						refreshTracks();
					}}
				>
					<i class="bi bi-search" />
				</button>
			</div>
		</div>
		<hr />
		<div class="row">
			<TrackList
				tracks={data.tracks}
				on:trackPlayButtonClicked={(event) =>
					(playerSrc = `api/tracks/${event.detail.trackId}/play`)}
				on:trackTagClicked={(event) => {
					document.querySelector('#search-input').value = event.detail.tag;
					refreshTracks();
				}}
			/>
		</div>
		<hr />
		<div class="row">
			<Player src={playerSrc} />
		</div>
	</div>
</div>
