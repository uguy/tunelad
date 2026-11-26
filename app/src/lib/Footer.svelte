<script lang="ts">
	import Time from 'svelte-time';
	// replaced dynamically
	const buildDate = __DATE__;
</script>

<footer class="mt-10 border-t border-line px-6 py-8 text-center text-sm text-ink-muted">
	<p>
		Tune Lad, a <a
			class="font-semibold text-accent-strong hover:underline"
			href="https://svelte.dev/docs/kit"
		>
			SvelteKit
		</a> PWA playground
	</p>
	<p class="mt-1">
		Last update :
		<Time timestamp={buildDate} format="dddd D MMMM YYYY" />
	</p>
</footer>
