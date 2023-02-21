<script>
	import '../app.css';
	import Header from '$lib/Header.svelte';
	import Footer from '$lib/Footer.svelte';

	import { onMount } from 'svelte';
	import { pwaInfo } from 'virtual:pwa-info';

	let ReloadPrompt;
	onMount(async () => {
		pwaInfo && (ReloadPrompt = (await import('$lib/ReloadPrompt.svelte')).default);
	});
	$: webManifest = pwaInfo ? pwaInfo.webManifest.linkTag : '';
</script>

<svelte:head>
	{@html webManifest}
</svelte:head>

<Header />
<main class="flex flex-row">
	<div class="lg:basis-1/6 md:hidden sm:hidden" />
	<div class="lg:basis-4/6 sm:ml-auto sm:mr-auto flex bg-white px-5 py-10 w-full">
		<slot />
	</div>
	<div class="lg:basis-1/6 md:hidden sm:hidden" />
</main>
<Footer />

{#if ReloadPrompt}
	<svelte:component this={ReloadPrompt} />
{/if}
