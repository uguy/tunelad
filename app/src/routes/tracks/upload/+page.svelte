<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { session } from '#lib/session.svelte.js';
	import TrackService, {
		ACCEPTED_FILES,
		checkFile,
		TrackUploadError,
		uploadErrorMessage
	} from '../Tracks.js';

	const trackService = new TrackService();

	// The form's values. They stay as they are after a failed or cancelled upload, file included (the input is
	// never unmounted), so submitting again retries the same upload.
	let title = $state('');
	let description = $state('');
	let artist = $state('');
	let album = $state('');
	let files = $state<FileList | null>(null);

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

		status = 'uploading';
		progress = 0;
		message = '';
		controller = new AbortController();
		try {
			const track = await trackService.upload(
				{ title, description, artist, album, file },
				{ onProgress: (fraction) => (progress = fraction), signal: controller.signal }
			);
			// The tracks page shows it at once: search only finds a new track a few seconds later
			await goto(resolve('/tracks'), { state: { uploaded: track } });
		} catch (e) {
			if (e instanceof DOMException && e.name === 'AbortError') {
				status = 'cancelled';
				message = 'The upload was cancelled. Your input is kept: submit again to retry.';
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
		<a href={resolve('/tracks')} class="text-sm font-medium text-ink-muted hover:text-ink"
			>← Tracks</a
		>
		<h1 class="mt-2 text-3xl font-bold tracking-tight">Upload a track</h1>
	</div>

	{#if session.account}
		<form class="card space-y-4 p-6" aria-label="upload a track" onsubmit={submit}>
			<label class={labelClass}
				>Audio file (mp3, m4a, ogg, flac or wav, up to 100 MB)
				<input
					class="mt-2 block w-full rounded-md border border-dashed border-line bg-canvas p-4 text-sm text-ink-muted file:mr-4 file:rounded-full file:border-0 file:bg-accent file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-accent-strong"
					name="file"
					type="file"
					accept={ACCEPTED_FILES}
					required
					bind:files
				/></label
			>
			<label class={labelClass}
				>Title <input class={inputClass} name="title" required bind:value={title} /></label
			>
			<label class={labelClass}
				>Description <textarea
					class={inputClass}
					rows="3"
					name="description"
					required
					bind:value={description}></textarea></label
			>
			<div class="grid gap-4 sm:grid-cols-2">
				<label class={labelClass}
					>Artist <input class={inputClass} name="artist" bind:value={artist} /></label
				>
				<label class={labelClass}
					>Album <input class={inputClass} name="album" bind:value={album} /></label
				>
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
				<p role="alert" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
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
			<a href="/login" rel="external" class="font-semibold text-accent-strong hover:underline"
				>Log in to upload</a
			> a track.
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
