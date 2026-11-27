<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import EmptyState from '#lib/ui/EmptyState.svelte';

	const notFound = $derived(page.status === 404);
</script>

<svelte:head>
	<title>Tune Lad - {notFound ? 'Page not found' : 'Something went wrong'}</title>
</svelte:head>

<EmptyState title={notFound ? 'Page not found' : 'Something went wrong'}>
	{notFound
		? 'There is nothing at this address.'
		: (page.error?.message ?? 'Try again in a moment.')}
	<a
		href={resolve('/tracks')}
		class="mt-4 inline-block font-semibold text-accent-ink hover:underline"
	>
		Back to the tracks
	</a>
</EmptyState>
