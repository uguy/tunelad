<script lang="ts">
	import '../app.css';

	import Header from '#lib/Header.svelte';
	import Footer from '#lib/Footer.svelte';

	import Player from './tracks/component/Player.svelte';
	import { player } from '#lib/player.svelte.js';

	import { pwaInfo } from 'virtual:pwa-info';

	let { children } = $props();

	const webManifest = pwaInfo ? pwaInfo.webManifest.linkTag : '';
</script>

<svelte:head>
	<!-- trusted: manifest link tag generated at build time by vite-plugin-pwa -->
	<!-- eslint-disable-next-line svelte/no-at-html-tags -->
	{@html webManifest}
</svelte:head>

<Header />
<main class="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
	{@render children()}
	<div class="py-10 w-full">
		<Player source={player.source} />
	</div>
</main>
<Footer />

{#await import('#lib/ReloadPrompt.svelte') then { default: ReloadPrompt }}
	<ReloadPrompt />
{/await}
