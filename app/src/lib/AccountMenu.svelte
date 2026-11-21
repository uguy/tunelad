<script lang="ts">
	import { resolve } from '$app/paths';
	import { session } from '#lib/session.svelte.js';
	import { csrfToken } from '#lib/api.js';

	let { mobile = false }: { mobile?: boolean } = $props();

	// The token is read when the form is submitted: the cookie may have been renewed since the page rendered
	function addCsrfToken(event: SubmitEvent) {
		const form = event.currentTarget as HTMLFormElement;
		(form.elements.namedItem('_csrf') as HTMLInputElement).value = csrfToken() ?? '';
	}

	const linkClass = $derived(
		mobile
			? 'text-white/70 hover:bg-white/10 hover:text-white block px-3 py-2 rounded-md text-base font-medium'
			: 'text-white/80 hover:text-white border border-white/30 hover:border-white px-3 py-1.5 rounded-full text-sm font-medium transition-colors'
	);
</script>

{#if session.account}
	<div class={mobile ? 'space-y-1' : 'flex items-center space-x-2'}>
		<span
			class={mobile
				? 'block px-3 py-2 text-base text-white'
				: 'px-3 py-2 text-sm font-medium text-white'}>{session.account.displayName}</span
		>
		<!-- A plain form post: the server ends the session, then redirects through the provider's logout -->
		<form method="post" action="/logout" onsubmit={addCsrfToken}>
			<input type="hidden" name="_csrf" value="" />
			<button type="submit" class={linkClass}>Log out</button>
		</form>
	</div>
{:else}
	<a href={resolve('/login')} class={linkClass}>Log in</a>
{/if}
