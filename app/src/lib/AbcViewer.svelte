<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { player } from './player.svelte.js';

	/** Where the flute samples are served from (static/soundfonts/flute-mp3, one file per note), as abcjs wants it: with a trailing slash. */
	const SOUNDFONT_URL = '/soundfonts/';
	/** General MIDI program 73, the flute, the instrument of the soundfont. */
	const FLUTE = 73;

	let { abc, title }: { abc: string | null | undefined; title: string } = $props();

	type Abcjs = typeof import('abcjs');
	type Synth = InstanceType<Abcjs['synth']['CreateSynth']>;

	let view = $state<'sheet' | 'abc'>('sheet');
	let sheet = $state<HTMLElement>();
	let abcjs = $state.raw<Abcjs | null>(null);
	let loadFailed = $state(false);
	let visualObj: ReturnType<Abcjs['renderAbc']>[number] | undefined;

	let playing = $state(false);
	let starting = $state(false);
	let tempo = $state(100);
	let error = $state('');
	let synth: Synth | undefined;
	let context: AudioContext | undefined;

	/** abcjs is CommonJS: depending on the bundler, its API is the module, its default export, or the default's default. */
	function resolveAbcjs(module: unknown): Abcjs {
		let candidate = module as Record<string, unknown>;
		for (let depth = 0; depth < 3 && typeof candidate.renderAbc !== 'function'; depth++) {
			candidate = candidate.default as Record<string, unknown>;
		}
		return candidate as unknown as Abcjs;
	}

	// abcjs is large: it is fetched once a tune with a transcription is opened, and by no other page
	onMount(() => {
		import('abcjs')
			.then((module) => (abcjs = resolveAbcjs(module)))
			.catch(() => (loadFailed = true));
		return stop;
	});

	// The sheet is drawn when the library is there and the sheet view is shown
	$effect(() => {
		if (!abcjs || !abc || view !== 'sheet' || !sheet) return;
		visualObj = abcjs.renderAbc(sheet, abc, { responsive: 'resize', add_classes: true })[0];
	});

	// Playing needs the sheet's parsed tune, so it also parses when only the ABC text is shown
	function parsed() {
		if (visualObj) return visualObj;
		const host = document.createElement('div');
		return abcjs!.renderAbc(host, abc!)[0];
	}

	async function play() {
		if (!abcjs || !abc || starting) return;
		error = '';
		starting = true;
		// The audio context is made inside the click, as browsers only start audio on a user gesture
		context = new AudioContext();
		try {
			if (!abcjs.synth.supportsAudio()) throw new Error('unsupported');
			player.paused = true;
			const tune = parsed();
			synth = new abcjs.synth.CreateSynth();
			await synth.init({
				visualObj: tune,
				audioContext: context,
				millisecondsPerMeasure: (tune.millisecondsPerMeasure() * 100) / tempo,
				options: {
					soundFontUrl: SOUNDFONT_URL,
					program: FLUTE,
					onEnded: () => stop()
				}
			});
			await synth.prime();
			synth.start();
			playing = true;
		} catch {
			stop();
			error = 'This tune could not be played here. Check that your browser allows audio.';
		} finally {
			starting = false;
		}
	}

	function stop() {
		synth?.stop();
		synth = undefined;
		void context?.close().catch(() => {});
		context = undefined;
		playing = false;
	}

	async function show(next: 'sheet' | 'abc') {
		view = next;
		await tick();
	}

	// Starting a track stops the tune: two sounds at once help nobody
	$effect(() => {
		if (!player.paused && playing) stop();
	});

	const tabClass = (current: boolean) =>
		`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
			current ? 'bg-accent text-on-accent' : 'text-ink hover:bg-surface-muted'
		}`;
</script>

{#if !abc}
	<p class="card p-6 text-ink-muted">This tune has no transcription yet.</p>
{:else}
	<div class="card flex flex-col gap-4 p-4 sm:p-6">
		<div class="flex flex-wrap items-center justify-between gap-3">
			<div
				role="group"
				aria-label="Transcription view"
				class="inline-flex rounded-full ring-1 ring-line"
			>
				<button
					type="button"
					class={tabClass(view === 'sheet')}
					aria-pressed={view === 'sheet'}
					onclick={() => show('sheet')}
				>
					Sheet music
				</button>
				<button
					type="button"
					class={tabClass(view === 'abc')}
					aria-pressed={view === 'abc'}
					onclick={() => show('abc')}
				>
					ABC text
				</button>
			</div>

			<div class="flex flex-wrap items-center gap-3">
				<label class="flex items-center gap-2 text-sm text-ink-muted">
					Tempo
					<input
						type="range"
						min="50"
						max="150"
						step="10"
						bind:value={tempo}
						disabled={playing || starting}
						aria-valuetext="{tempo}%"
					/>
					<span class="w-10 tabular-nums" aria-hidden="true">{tempo}%</span>
				</label>
				{#if playing}
					<button type="button" class="btn-accent" onclick={stop}>Stop</button>
				{:else}
					<button type="button" class="btn-accent" onclick={play} disabled={!abcjs || starting}>
						{starting ? 'Loading sound…' : 'Play'}
					</button>
				{/if}
			</div>
		</div>

		{#if error}
			<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
		{/if}
		<p class="sr-only" role="status">{playing ? `Playing ${title}` : ''}</p>

		{#if loadFailed}
			<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
				The sheet music could not be loaded. The ABC text is still available.
			</p>
		{/if}

		{#if view === 'sheet' && !loadFailed}
			<div
				bind:this={sheet}
				class="overflow-x-auto text-ink"
				role="img"
				aria-label="Sheet music of {title}. The ABC text view gives the notes as text."
			>
				{#if !abcjs}<p class="text-ink-muted">Loading the sheet music…</p>{/if}
			</div>
		{:else}
			<!-- A scrollable region must be reachable by keyboard -->
			<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
			<pre
				class="overflow-x-auto rounded-md bg-canvas p-4 text-sm"
				aria-label="ABC notation of {title}"
				tabindex="0">{abc}</pre>
		{/if}
	</div>
{/if}
