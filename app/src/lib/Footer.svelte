<script>
	import Time from 'svelte-time';
	// replaced dynamically
	const buildDate = __DATE__;
</script>

<footer>
	<p class="text-center">
		Simple <a href="https://kit.svelte.dev">SvelteKit</a> PWA demo
		<br />
		Last update :
		<Time timestamp={buildDate} format="dddd D MMMM YYYY" />
	</p>
</footer>

<style>
	footer {
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		padding: 40px;
	}

	footer a {
		font-weight: bold;
	}

	@media (min-width: 480px) {
		footer {
			padding: 40px 0;
		}
	}
</style>
