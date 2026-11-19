<script lang="ts">
	import '../app.css';

	import Header from '#lib/Header.svelte';
	import Footer from '#lib/Footer.svelte';

	import Player from './tracks/component/Player.svelte';
	import playerSource from '#lib/store/media-source.js';

	import { pwaInfo } from 'virtual:pwa-info';
	const webManifest = pwaInfo ? pwaInfo.webManifest.linkTag : '';
</script>

<svelte:head>
	<!-- trusted: manifest link tag generated at build time by vite-plugin-pwa -->
	<!-- eslint-disable-next-line svelte/no-at-html-tags -->
	{@html webManifest}
</svelte:head>

<Header />
<main class="relative flex flex-row">
	<div class="lg:basis-1/6 md:hidden sm:hidden"></div>
	<div class="lg:basis-4/6 sm:ml-auto sm:mr-auto flex bg-white px-5 py-10 w-full">
		<div class="flex flex-col w-full">
			<div class="w-full">
				<slot />
			</div>
			<div class="py-10 w-full">
				<Player source={$playerSource} />
			</div>
		</div>
	</div>
	<div class="lg:basis-1/6 md:hidden sm:hidden"></div>
</main>
<Footer />

{#await import('#lib/ReloadPrompt.svelte') then { default: ReloadPrompt }}
	<ReloadPrompt />
{/await}
