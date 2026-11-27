<script lang="ts">
	import { onDestroy, onMount } from 'svelte';
	import { request } from '#lib/api.js';

	/** Called when a run has finished, so that the list shows the new tunes. */
	let { ondone }: { ondone: () => void } = $props();

	interface Run {
		state: 'idle' | 'running' | 'done' | 'failed';
		created: number;
		skipped: number;
		reason: string | null;
	}

	let run = $state.raw<Run | null>(null);
	let problem = $state('');
	let timer: ReturnType<typeof setInterval> | undefined;

	async function load() {
		const res = await request(fetch, 'GET', 'tunes/import-dump');
		if (!res.ok) return;
		const previous = run?.state;
		run = await res.json();
		if (run?.state !== 'running') stop();
		if (previous === 'running' && run?.state !== 'running') ondone();
	}

	function stop() {
		clearInterval(timer);
		timer = undefined;
	}

	function poll() {
		timer ??= setInterval(() => void load(), 2000);
	}

	async function start() {
		problem = '';
		const res = await request(fetch, 'POST', 'tunes/import-dump');
		if (res.status === 409) {
			problem = 'An import is running already.';
		} else if (!res.ok) {
			problem = 'The import could not be started.';
			return;
		}
		run = await res.json().catch(() => run);
		poll();
		await load();
	}

	onMount(async () => {
		await load();
		if (run?.state === 'running') poll();
	});
	onDestroy(stop);

	const message = $derived.by(() => {
		if (!run || run.state === 'idle') return '';
		const counts = `${run.created} created, ${run.skipped} skipped`;
		if (run.state === 'running') return `Importing: ${counts}…`;
		if (run.state === 'failed')
			return `Import failed (${run.reason ?? 'unknown reason'}): ${counts}.`;
		return `Import finished: ${counts}.`;
	});
</script>

<section class="card flex flex-wrap items-center gap-3 p-4" aria-label="import from thesession.org">
	<button
		type="button"
		class="btn-accent"
		onclick={start}
		disabled={run?.state === 'running'}
		aria-describedby="dump-import-status"
	>
		Import tunes from thesession.org
	</button>
	<p
		id="dump-import-status"
		role="status"
		class="text-sm text-ink-muted"
		data-testid="dump-import-status"
	>
		{message}
	</p>
	{#if problem}
		<p role="alert" class="text-sm text-danger">{problem}</p>
	{/if}
</section>
