<script>
	import { createEventDispatcher } from 'svelte';

	export let tracks;
	export const TRACK_PLAY_BUTTON_CLICKED = 'trackPlayButtonClicked';

	const dispatch = createEventDispatcher();
</script>

<div id="trackList">
	{#if tracks.length === 0}
		<div>No tracks found.</div>
	{:else}
		{#each tracks as track}
			<details class="card">
				<summary class="card-header">
					<button
						class="btn btn-secondary btn-sm"
						on:click={() => dispatch(TRACK_PLAY_BUTTON_CLICKED, { trackId: track.id })}
					>
						<i class="bi bi-play-circle" />
					</button>
					<span>{track.title}</span>
				</summary>
				<div class="card-body">
					<h4>Album : {track.album}</h4>
					<p>{track.description}</p>
				</div>
				<div class="card-footer">
					{#each track.tags as tag}
						<span class="badge rounded-pill bg-secondary">{tag}</span>
					{/each}
				</div>
			</details>
		{/each}
	{/if}
</div>
