<script lang="ts">
	import { resolve } from '$app/paths';
	import TrackList from '../component/TrackList.svelte';
	import EmptyState from '#lib/ui/EmptyState.svelte';
	import PageHeader from '#lib/ui/PageHeader.svelte';
	import { favourites } from '#lib/favourites.svelte.js';
	import { session } from '#lib/session.svelte.js';
</script>

<svelte:head>
	<title>Tune Lad - Favourites</title>
</svelte:head>

<section class="flex flex-col gap-6">
	<PageHeader title="Favourites" subtitle="The tracks you marked, the latest first." />

	{#if !session.loaded}
		<!-- Who is logged in is not known yet -->
	{:else if !session.account}
		<EmptyState title="Log in to see your favourites">
			Favourites belong to your account.
			<!-- Served by the server, not the SPA router: it lists the login providers -->
			<a href="/login" rel="external" class="btn-accent mt-4">Log in</a>
		</EmptyState>
	{:else if favourites.loaded && favourites.tracks.length === 0}
		<EmptyState title="No favourites yet">
			Mark a track with the heart and it shows up here.
			<a href={resolve('/tracks')} class="btn-accent mt-4">Browse tracks</a>
		</EmptyState>
	{:else}
		<TrackList tracks={favourites.tracks} />
	{/if}
</section>
