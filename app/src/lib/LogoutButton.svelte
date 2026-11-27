<script lang="ts">
	import { csrfToken } from '#lib/api.js';

	let { class: className = 'btn-quiet', role }: { class?: string; role?: 'menuitem' } = $props();

	// The token is read when the form is submitted: the cookie may have been renewed since the page rendered
	function addCsrfToken(event: SubmitEvent) {
		const form = event.currentTarget as HTMLFormElement;
		(form.elements.namedItem('_csrf') as HTMLInputElement).value = csrfToken() ?? '';
	}
</script>

<!-- A plain form post: the server ends the session, then redirects through the provider's logout -->
<form method="post" action="/logout" onsubmit={addCsrfToken}>
	<input type="hidden" name="_csrf" value="" />
	<button type="submit" {role} class={className}>Log out</button>
</form>
