<script lang="ts">
	import { onMount } from 'svelte';
	import { resolve } from '$app/paths';
	import { live } from './live.svelte.js';
	import { session } from './session.svelte.js';
	import CommentService, {
		CommentError,
		MAX_COMMENT_LENGTH,
		mayChange,
		type Comment,
		type CommentSubject
	} from './comments.js';

	let { subject, id }: { subject: CommentSubject; id: string } = $props();

	const inputClass =
		'mt-1 w-full rounded-md border border-line bg-surface px-3 py-2 text-sm text-ink focus-visible:outline-2 focus-visible:outline-accent';
	const service = new CommentService();
	const heading = $derived(`comments-${subject}-${id}`);

	let comments = $state.raw<Comment[]>([]);
	let loaded = $state(false);
	let failed = $state(false);
	let text = $state('');
	let error = $state('');
	let busy = $state(false);
	let editing = $state<string | null>(null);
	let editText = $state('');

	const date = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

	async function load() {
		try {
			comments = await service.list(subject, id);
			failed = false;
		} catch {
			failed = true;
		}
		loaded = true;
	}

	/** Runs a change, shows what the server refused, and always ends up showing the server's state. */
	async function change(action: () => Promise<unknown>): Promise<boolean> {
		busy = true;
		error = '';
		try {
			await action();
			await load();
			return true;
		} catch (e) {
			error = e instanceof CommentError ? e.message : 'The comment could not be saved.';
			return false;
		} finally {
			busy = false;
		}
	}

	async function post(event: SubmitEvent) {
		event.preventDefault();
		if (!text.trim()) return;
		if (await change(() => service.post(subject, id, text))) text = '';
	}

	function startEdit(comment: Comment) {
		editing = comment.id;
		editText = comment.text;
		error = '';
	}

	async function saveEdit(event: SubmitEvent) {
		event.preventDefault();
		const target = editing;
		if (!target || !editText.trim()) return;
		if (await change(() => service.edit(target, editText))) editing = null;
	}

	// Events carry the id of the subject: refetch only when it is ours, and after a lost connection
	onMount(() => {
		void load();
		const stops = [
			live.on('comment.changed', (event) => event.id === id && void load()),
			live.onReconnect(() => void load())
		];
		return () => stops.forEach((stop) => stop());
	});
</script>

<section aria-labelledby={heading} class="flex flex-col gap-3">
	<h2 id={heading} class="text-lg font-semibold">
		Comments{#if loaded && !failed}<span class="ml-1 text-sm font-normal text-ink-muted">
				({comments.length})
			</span>{/if}
	</h2>

	{#if failed}
		<p role="alert" class="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">
			The comments could not be loaded.
		</p>
	{:else if loaded && comments.length === 0}
		<p class="text-sm text-ink-muted">No comments yet.</p>
	{/if}

	<ul class="flex flex-col gap-3" aria-live="polite">
		{#each comments as comment (comment.id)}
			<li class="rounded-md border border-line px-3 py-2" data-testid="comment">
				<p class="text-xs text-ink-muted">
					<span class="font-medium text-ink">{comment.authorName ?? 'Someone'}</span>
					· <time datetime={comment.createdAt}>{date.format(new Date(comment.createdAt))}</time>
					{#if comment.editedAt}· edited{/if}
				</p>
				{#if editing === comment.id}
					<form class="mt-2 flex flex-col gap-2" onsubmit={saveEdit}>
						<label class="block text-sm font-medium">
							Edit comment
							<textarea
								class={inputClass}
								rows="3"
								maxlength={MAX_COMMENT_LENGTH}
								required
								bind:value={editText}></textarea>
						</label>
						<div class="flex gap-2">
							<button type="submit" class="btn-accent" disabled={busy}>Save</button>
							<button type="button" class="btn-quiet" onclick={() => (editing = null)}>
								Cancel
							</button>
						</div>
					</form>
				{:else}
					<p class="mt-1 text-sm whitespace-pre-wrap">{comment.text}</p>
					{#if mayChange(session.account, comment)}
						<div class="mt-1 flex gap-3 text-sm">
							<button
								type="button"
								class="btn-quiet"
								aria-label="Edit comment by {comment.authorName ?? 'someone'}"
								onclick={() => startEdit(comment)}
							>
								Edit
							</button>
							<button
								type="button"
								class="btn-quiet"
								aria-label="Delete comment by {comment.authorName ?? 'someone'}"
								disabled={busy}
								onclick={() => change(() => service.remove(comment.id))}
							>
								Delete
							</button>
						</div>
					{/if}
				{/if}
			</li>
		{/each}
	</ul>

	{#if error}
		<p role="alert" class="text-sm text-danger">{error}</p>
	{/if}

	{#if session.account}
		<form class="flex flex-col gap-2" onsubmit={post}>
			<label class="block text-sm font-medium">
				Add a comment
				<textarea
					class={inputClass}
					rows="3"
					maxlength={MAX_COMMENT_LENGTH}
					required
					bind:value={text}></textarea>
			</label>
			<div><button type="submit" class="btn-accent" disabled={busy}>Post</button></div>
		</form>
	{:else if session.loaded}
		<p class="text-sm text-ink-muted">
			<a class="underline hover:text-ink" href={resolve('/login')}>Log in</a> to comment.
		</p>
	{/if}
</section>
