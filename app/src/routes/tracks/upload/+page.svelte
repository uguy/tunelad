<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onMount, untrack } from 'svelte';
	import { session } from '#lib/session.svelte.js';
	import type { Tune } from '../../tunes/Tunes.js';
	import SetBuilder from '../../sets/SetBuilder.svelte';
	import SetService, { SetError, setTitle, type TuneSet } from '../../sets/Sets.js';
	import PerformanceFields from '../PerformanceFields.svelte';
	import {
		checkPerformance,
		emptyPerformances,
		toInput,
		type PerformanceForm
	} from '../Performances.js';
	import TrackService, {
		ACCEPTED_FILES,
		checkFile,
		TrackUploadError,
		uploadErrorMessage
	} from '../Tracks.js';

	const trackService = new TrackService();
	const setService = new SetService();

	/** The value of the set field that stands for building a new set. */
	const NEW_SET = 'new';

	// The form's values. They stay as they are after a failed or cancelled upload, file included (the input is
	// never unmounted), so submitting again retries the same upload.
	let title = $state('');
	let description = $state('');
	let artist = $state('');
	let album = $state('');
	let files = $state<FileList | null>(null);

	/** The sets to choose from. */
	let sets = $state.raw<TuneSet[]>([]);
	/** The set field: empty, an existing set's id, or {@link NEW_SET}. */
	let setChoice = $state('');
	/** The tunes of the new set being built. */
	let chosen = $state.raw<Tune[]>([]);
	/** The performance fields, one per tune of the set. */
	let performances = $state<PerformanceForm[]>([]);

	onMount(async () => {
		try {
			sets = await setService.findAll();
		} catch {
			sets = [];
		}
	});

	/** The names of the tunes of the chosen set, in order: one group of performance fields each. */
	const tuneNames = $derived(
		setChoice === NEW_SET
			? chosen.map((tune) => tune.name)
			: (sets.find((set) => set.id === setChoice)?.tunes.map((tune) => tune.name) ?? [])
	);

	// One form per tune: a changed set starts from empty fields, an added or removed tune keeps the others
	$effect(() => {
		const count = tuneNames.length;
		untrack(() => {
			if (performances.length !== count)
				performances = [...performances, ...emptyPerformances(count)].slice(0, count);
		});
	});

	function chooseSet() {
		performances = emptyPerformances(tuneNames.length);
	}

	let status = $state<'editing' | 'uploading' | 'cancelled' | 'failed'>('editing');
	/** The share of the file sent so far, from 0 to 1. */
	let progress = $state(0);
	let message = $state('');
	/** The HTTP status of the last refusal: 401 offers to log in again. */
	let refusedWith = $state<number | null>(null);
	let controller: AbortController | null = null;

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		const file = files?.[0];
		if (!file) return;
		const refusal = checkFile(file);
		if (refusal) {
			fail(refusal, null);
			return;
		}

		if (!setChoice || (setChoice === NEW_SET && chosen.length === 0)) {
			fail('Choose a set, or build one from its tunes.', null);
			return;
		}
		const invalid = performances.map(checkPerformance).find(Boolean);
		if (invalid) {
			fail(invalid, null);
			return;
		}

		status = 'uploading';
		progress = 0;
		message = '';
		controller = new AbortController();
		try {
			// A new set is created first: the same list always gives the same set, so a retry finds it again
			const setId =
				setChoice === NEW_SET
					? (await setService.create(chosen.map((tune) => tune.id))).set.id
					: setChoice;
			const track = await trackService.upload(
				{
					title,
					description,
					artist,
					album,
					setId,
					performances: performances.map(toInput),
					file
				},
				{ onProgress: (fraction) => (progress = fraction), signal: controller.signal }
			);
			// The tracks page shows it at once: search only finds a new track a few seconds later
			await goto(resolve('/tracks'), { state: { uploaded: track } });
		} catch (e) {
			if (e instanceof DOMException && e.name === 'AbortError') {
				status = 'cancelled';
				message = 'The upload was cancelled. Your input is kept: submit again to retry.';
			} else if (e instanceof SetError) {
				fail(e.message, e.status);
			} else if (e instanceof TrackUploadError) {
				fail(e.message, e.status);
			} else {
				fail(uploadErrorMessage(0), 0);
			}
		} finally {
			controller = null;
		}
	}

	function fail(text: string, httpStatus: number | null) {
		status = 'failed';
		message = text;
		refusedWith = httpStatus;
	}

	function cancel() {
		controller?.abort();
	}

	const labelClass = 'block text-sm font-medium';
	const inputClass =
		'mt-1 block w-full rounded-md border-line text-sm focus:border-accent focus:ring-accent';
</script>

<svelte:head>
	<title>Tune Lad - Upload a track</title>
</svelte:head>

<section class="mx-auto flex w-full max-w-2xl flex-col gap-6">
	<div>
		<a href={resolve('/tracks')} class="text-sm font-medium text-ink-muted hover:text-ink">
			← Tracks
		</a>
		<h1 class="mt-2 text-3xl font-bold tracking-tight">Upload a track</h1>
	</div>

	{#if session.account}
		<form class="card space-y-4 p-6" aria-label="upload a track" onsubmit={submit}>
			<label class={labelClass}>
				Audio file (mp3, m4a, ogg, flac, wav or wma, up to 100 MB)
				<input
					class="mt-2 block w-full rounded-md border border-dashed border-line bg-canvas p-4 text-sm text-ink-muted file:mr-4 file:rounded-full file:border-0 file:bg-accent file:px-4 file:py-2 file:text-sm file:font-semibold file:text-on-accent hover:file:bg-accent-strong"
					name="file"
					type="file"
					accept={ACCEPTED_FILES}
					required
					bind:files
				/>
			</label>
			<label class={labelClass}>
				Title <input class={inputClass} name="title" required bind:value={title} />
			</label>
			<label class={labelClass}>
				Description <textarea
					class={inputClass}
					rows="3"
					name="description"
					required
					bind:value={description}></textarea>
			</label>
			<label class={labelClass}>
				Set
				<select class={inputClass} name="set" required bind:value={setChoice} onchange={chooseSet}>
					<option value="">Choose a set</option>
					{#each sets as set (set.id)}
						<option value={set.id}>{setTitle(set)}</option>
					{/each}
					<option value={NEW_SET}>Build a new set…</option>
				</select>
			</label>
			{#if setChoice === NEW_SET}
				<SetBuilder bind:chosen headingLevel={2} />
			{/if}
			{#if tuneNames.length > 0}
				<div class="space-y-3">
					<h2 class="text-lg font-semibold">Performances</h2>
					<p class="text-sm text-ink-muted">
						All optional: where each tune starts, its key and notes.
					</p>
					<PerformanceFields names={tuneNames} bind:values={performances} />
				</div>
			{/if}
			<div class="grid gap-4 sm:grid-cols-2">
				<label class={labelClass}>
					Artist <input class={inputClass} name="artist" bind:value={artist} />
				</label>
				<label class={labelClass}>
					Album <input class={inputClass} name="album" bind:value={album} />
				</label>
			</div>

			{#if status === 'uploading'}
				<div class="flex items-center gap-3">
					<progress
						class="upload-progress h-2 w-full overflow-hidden rounded-full"
						max="1"
						value={progress}
						aria-label="upload progress"
					></progress>
					<span class="w-10 text-right text-sm tabular-nums">{Math.round(progress * 100)}%</span>
				</div>
			{/if}

			{#if message}
				<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
					{message}
					{#if refusedWith === 401}
						<a href="/login" rel="external" class="font-medium underline">Log in</a>
					{/if}
				</p>
			{/if}

			<div class="flex gap-2 pt-2">
				<button type="submit" class="btn-accent" disabled={status === 'uploading'}>Upload</button>
				{#if status === 'uploading'}
					<button type="button" class="btn-quiet" onclick={cancel}>Cancel</button>
				{:else}
					<a href={resolve('/tracks')} class="btn-quiet">Back to tracks</a>
				{/if}
			</div>
		</form>
	{:else}
		<p class="card p-6">
			<!-- Served by the server, not the SPA router: it lists the login providers -->
			<a href="/login" rel="external" class="font-semibold text-accent-ink hover:underline">
				Log in to upload
			</a> a track.
		</p>
	{/if}
</section>

<style>
	.upload-progress {
		appearance: none;
		background-color: var(--color-line);
	}
	.upload-progress::-webkit-progress-bar {
		background-color: var(--color-line);
	}
	.upload-progress::-webkit-progress-value {
		background-color: var(--color-accent);
	}
	.upload-progress::-moz-progress-bar {
		background-color: var(--color-accent);
	}
</style>
