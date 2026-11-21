<script lang="ts">
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>Tune Lad - Log in</title>
</svelte:head>

<section class="mx-auto flex max-w-sm flex-col gap-6 py-8">
	<div class="flex flex-col gap-2 text-center">
		<h1 class="text-3xl font-bold tracking-tight">Log in</h1>
		<p class="text-ink-muted">Log in to upload and manage your tracks.</p>
	</div>

	<div class="card flex flex-col gap-3 p-6">
		{#each data.providers as provider (provider.id)}
			<!-- Spring starts the login and redirects to the provider: a full page load, not a SPA navigation -->
			<a href="/oauth2/authorization/{provider.id}" rel="external" class="btn-accent w-full py-2.5">
				Continue with {provider.name}
			</a>
		{:else}
			<p class="text-center text-ink-muted">No login provider is configured.</p>
		{/each}
	</div>
</section>
