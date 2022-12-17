<script>
    export let tracks;
    import {createEventDispatcher} from 'svelte';

    const dispatch = createEventDispatcher();

</script>

<div id="trackList">
    {#if tracks.length === 0}
        <div>No tracks here... yet.</div>
    {:else}
        {#each tracks as track}
            <details>
                <summary>
                    <button
                        class="btn btn-secondary btn-sm"
                        on:click|preventDefault={() => {
                            dispatch('trackToBePlayed', {
                                trackId: track.id
                            });
                        }}
                    >
                        <i class="bi bi-play-circle"/>
                    </button>
                    <span>{track.title}</span>
                </summary>
                <div class="card card-body">
                    <h5>Album : {track.album}</h5>
                    <p>{track.description}</p>
                    <div>
                        {#each track.tags as tag}
                            <span class="badge rounded-pill bg-light text-dark">{tag}</span>
                        {/each}
                    </div>
                </div>
            </details>
        {/each}
    {/if}
</div>
