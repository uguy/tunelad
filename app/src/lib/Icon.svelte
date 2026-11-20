<script lang="ts">
	import { icons, type IconName } from './icons.js';

	let { name, class: className = 'h-5 w-5' }: { name: IconName; class?: string } = $props();
</script>

<svg viewBox="0 0 24 24" fill="currentColor" class={className} aria-hidden="true">
	<path d={icons[name]} />
</svg>
