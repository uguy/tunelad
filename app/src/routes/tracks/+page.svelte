<script>
	import * as api from '$lib/api.js';

	import TrackList from './component/TrackList.svelte';

	/** @type {import('./$types').PageData} */
	export let data;

	async function refreshTracks() {
		const q = document.getElementById('search-input').value;
        data.tracks = await api.get('search/tracks?q=' + q);
	}

	function playTrack(trackId) {
        let player = document.getElementById('player');
        player.src = `api/tracks/${trackId}/play`;
		player.play();
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
			<TrackList tracks={data.tracks} playFunction={playTrack} />
		</div>
		<hr />
		<div class="row">
			<audio id="player" controls preload="none" style="width: 100%"></audio>
		</div>
	</div>
</div>
