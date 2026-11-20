<script lang="ts">
	import me from '#lib/store/me.js';
	import { csrfToken } from '#lib/api.js';

	let { mobile = false }: { mobile?: boolean } = $props();

	// The token is read when the form is submitted: the cookie may have been renewed since the page rendered
	function addCsrfToken(event: SubmitEvent) {
		const form = event.currentTarget as HTMLFormElement;
		(form.elements.namedItem('_csrf') as HTMLInputElement).value = csrfToken() ?? '';
	}

	const linkClass = $derived(
		mobile
			? 'text-gray-300 hover:bg-gray-700 hover:text-white block px-3 py-2 rounded-md text-base font-medium'
			: 'text-gray-300 hover:bg-gray-700 hover:text-white px-3 py-2 rounded-md text-sm font-medium'
	);
</script>

{#if $me}
	<div class={mobile ? 'space-y-1' : 'flex items-center space-x-2'}>
		<span class={mobile ? 'block px-3 py-2 text-base text-white' : 'px-3 py-2 text-sm text-white'}
			>{$me.displayName}</span
		>
		<!-- A plain form post: the server ends the session, then redirects through the provider's logout -->
		<form method="post" action="/logout" onsubmit={addCsrfToken}>
			<input type="hidden" name="_csrf" value="" />
			<button type="submit" class={linkClass}>Log out</button>
		</form>
	</div>
{:else}
	<!-- Served by the server, not the SPA router: it lists the login providers -->
	<a href="/login" rel="external" class={linkClass}>Log in</a>
{/if}
