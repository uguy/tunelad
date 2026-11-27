<script lang="ts">
	import '../app.css';

	import Header from '#lib/Header.svelte';
	import Footer from '#lib/Footer.svelte';

	import PlayerBar from '#lib/PlayerBar.svelte';
	import Toast from '#lib/ui/Toast.svelte';
	import { player } from '#lib/player.svelte.js';
	import { live } from '#lib/live.svelte.js';
	import { session } from '#lib/session.svelte.js';
	import { favourites } from '#lib/favourites.svelte.js';
	import { theme } from '#lib/theme.svelte.js';
	import { onMount } from 'svelte';

	import { pwaInfo } from 'virtual:pwa-info';

	let { children } = $props();

	const webManifest = pwaInfo ? pwaInfo.webManifest.linkTag : '';

	// Keeps the theme applied, and follows the system's while the user has not chosen
	onMount(() => theme.start());

	// The favourites of whoever is logged in; none for an anonymous visitor
	$effect(() => {
		if (!session.loaded) return;
		if (session.account?.id) void favourites.load();
		else favourites.clear();
	});

	// One live connection for the tab, opened once it is known who is logged in, and reopened if that changes
	onMount(() => live.follow(() => (session.loaded ? (session.account?.id ?? null) : undefined)));
</script>

<svelte:head>
	<!-- trusted: manifest link tag generated at build time by vite-plugin-pwa -->
	<!-- eslint-disable-next-line svelte/no-at-html-tags -->
	{@html webManifest}
</svelte:head>

<Header />
<!-- Room at the bottom for the player bar, so it never covers the end of the page -->
<div class={player.source ? 'pb-20' : ''}>
	<main class="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
		{@render children()}
	</main>
	<Footer />
</div>

<Toast />

<!-- In the root layout, so it stays mounted, and keeps playing, while the user moves between pages -->
{#if player.source}
	<PlayerBar />
{/if}

{#await import('#lib/ReloadPrompt.svelte') then { default: ReloadPrompt }}
	<ReloadPrompt />
{/await}
