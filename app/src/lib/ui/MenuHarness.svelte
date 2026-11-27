<script lang="ts">
	import Menu from './Menu.svelte';
</script>

<p>outside</p>
<Menu label="Open">
	{#snippet trigger()}<span class="sr-only">Open</span>{/snippet}
	<button type="button" role="menuitem">First</button>
	<button type="button" role="menuitem">Second</button>
</Menu>
