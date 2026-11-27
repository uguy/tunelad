<script lang="ts">
	import { resolve } from '$app/paths';
</script>

<svelte:head>
	<title>Tune Lad</title>
</svelte:head>

<section class="flex flex-col gap-8">
	<div class="relative overflow-hidden rounded-lg bg-header shadow-md">
		<img
			class="absolute inset-0 h-full w-full object-cover opacity-50"
			src="images/celtic-connections.webp"
			alt="celtic connections concert"
			width="600"
			height="364"
		/>
		<div class="absolute inset-0 bg-gradient-to-r from-header via-header/70 to-transparent"></div>
		<div class="relative px-6 py-16 sm:px-12 sm:py-24">
			<h1 class="text-3xl font-bold tracking-tight text-on-header sm:text-5xl">
				Welcome to Tune Lad !
			</h1>
			<p class="mt-4 max-w-md text-lg text-on-header/80">
				Share your tunes, and listen to everybody else's, right in the browser.
			</p>
			<a href={resolve('/tracks')} class="btn-accent mt-8 px-6 py-3 text-base">Start listening</a>
		</div>
	</div>
</section>
