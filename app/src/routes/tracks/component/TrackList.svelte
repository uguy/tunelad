<script>
	import { createEventDispatcher } from 'svelte';

	export let tracks = [];

	// Events dispatched externally
	export const TRACK_PLAY_BUTTON_CLICKED = 'trackPlayButtonClicked';
	export const TRACK_TAG_CLICKED = 'trackTagClicked';

	const dispatch = createEventDispatcher();
</script>

<div id="trackList flex w-full">
	{#if tracks.length === 0}
		<div>No tracks found.</div>
	{:else}
		{#each tracks as track}
			<details class="w-full p-4 border-b border-grey-lighter hover:shadow-lg">
				<summary class="flex items-center w-full hover:underline">
					<button
						id="play-track-{track.id}-btn"
						aria-label="play track {track.title} button"
						class=""
						on:click={() => dispatch(TRACK_PLAY_BUTTON_CLICKED, { trackId: track.id })}
						on:keypress={() => dispatch(TRACK_PLAY_BUTTON_CLICKED, { trackId: track.id })}
					>
						<svg
							xmlns="http://www.w3.org/2000/svg"
							viewBox="0 0 24 24"
							fill="currentColor"
							class="w-6 h-6 text-2xl text-gray-400"
						>
							<path
								fill-rule="evenodd"
								d="M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12zm14.024-.983a1.125 1.125 0 010 1.966l-5.603 3.113A1.125 1.125 0 019 15.113V8.887c0-.857.921-1.4 1.671-.983l5.603 3.113z"
								clip-rule="evenodd"
							/>
						</svg>
					</button>
					<span class="px-2">{track.title}</span>

					<button class="btn-detail ml-auto" aria-label="show track {track.title} detail button">
						<svg
							class="fill-current opacity-75 w-4 h-4 -mr-1"
							xmlns="http://www.w3.org/2000/svg"
							viewBox="0 0 20 20"
						>
							<path
								d="M12.95 10.707l.707-.707L8 4.343 6.586 5.757 10.828 10l-4.242 4.243L8 15.657l4.95-4.95z"
							/>
						</svg>
					</button>
				</summary>
				<div class="mt-4 leading-normal text-md">
					<b>Album : {track.album}</b>
					<p class="my-2 text-sm">{track.description}</p>
				</div>
				<div class="card-footer flex flex-wrap">
					{#each track.tags as tag}
						<span
							class="text-sm font-medium bg-gray-300 rounded-lg mr-2 my-1 px-2 py-1 hover:cursor-pointer"
							on:click={() => dispatch(TRACK_TAG_CLICKED, { tag: tag })}
							on:keypress={() => dispatch(TRACK_TAG_CLICKED, { tag: tag })}>{tag}</span
						>
					{/each}
				</div>
			</details>
		{/each}
	{/if}
</div>

<style>
	details {
		user-select: none;
	}

	details > summary > button.btn-detail > svg {
		transform: rotate(90deg);
	}

	details[open] > summary > button.btn-detail > svg {
		transform: rotate(-90deg);
	}

	details[open] > summary ~ * {
		animation: ease-opacity-t-b 0.5s ease;
	}

	summary {
		cursor: pointer;
	}

	svg {
		transition: all 0.3s;
	}

	/* TO JE TO - TO JE TAJ */
	summary::-webkit-details-marker {
		display: none;
	}
</style>
