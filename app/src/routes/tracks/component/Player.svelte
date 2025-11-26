<script>
	export let id = 'player';
	export let src = '';
	export let autoplay = true;
</script>

<audio {id} {src} controls preload="none" class="w-full" {autoplay}></audio>
