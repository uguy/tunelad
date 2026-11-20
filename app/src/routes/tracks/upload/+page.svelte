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
</script>

<svelte:head>
	<title>Tune Lad - Upload a track</title>
</svelte:head>

<section class="flex flex-col w-full space-y-5">
	<h1 class="text-3xl leading-2">Upload a track</h1>

	{#if session.account}
		<form class="space-y-3 max-w-xl" aria-label="upload a track" onsubmit={submit}>
			<label class="block text-sm"
				>Title <input class="block w-full" name="title" required bind:value={title} /></label
			>
			<label class="block text-sm"
				>Description <textarea
					class="block w-full"
					name="description"
					required
					bind:value={description}></textarea></label
			>
			<label class="block text-sm"
				>Artist <input class="block w-full" name="artist" bind:value={artist} /></label
			>
			<label class="block text-sm"
				>Album <input class="block w-full" name="album" bind:value={album} /></label
			>
			<label class="block text-sm"
				>Audio file (mp3, m4a, ogg, flac or wav, up to 100 MB)
				<input
					class="block w-full"
					name="file"
					type="file"
					accept={ACCEPTED_FILES}
					required
					bind:files
				/></label
			>

			{#if status === 'uploading'}
				<div class="flex items-center gap-2">
					<progress class="w-full" max="1" value={progress} aria-label="upload progress"></progress>
					<span class="text-sm tabular-nums">{Math.round(progress * 100)}%</span>
				</div>
			{/if}

			{#if message}
				<p role="alert" class="text-sm text-red-700">
					{message}
					{#if refusedWith === 401}
						<a href="/login" rel="external" class="underline">Log in</a>
					{/if}
				</p>
			{/if}

			<div class="flex gap-2">
				<button
					type="submit"
					class="text-sm px-3 py-1 rounded bg-gray-900 text-white disabled:opacity-50"
					disabled={status === 'uploading'}>Upload</button
				>
				{#if status === 'uploading'}
					<button type="button" class="text-sm px-3 py-1 rounded bg-gray-200" onclick={cancel}
						>Cancel</button
					>
				{:else}
					<a href={resolve('/tracks')} class="text-sm px-3 py-1 rounded bg-gray-200"
						>Back to tracks</a
					>
				{/if}
			</div>
		</form>
	{:else}
		<p>
			<!-- Served by the server, not the SPA router: it lists the login providers -->
			<a href="/login" rel="external" class="underline">Log in to upload</a> a track.
		</p>
	{/if}
</section>
