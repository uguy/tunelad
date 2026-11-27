<script lang="ts">
	import ArtistService, {
		ArtistError,
		distinctArtists,
		parseMembers,
		type Artist,
		type ArtistKind,
		type PerformanceArtist
	} from './artists.js';
	import { session } from './session.svelte.js';

	let { performances }: { performances: PerformanceArtist[] } = $props();

	const service = new ArtistService();
	const inputClass =
		'mt-1 w-full rounded-md border border-line bg-surface px-3 py-2 text-sm text-ink focus-visible:outline-2 focus-visible:outline-accent';
	const kindLabels = { musician: 'Musician', band: 'Band' };

	const artists = $derived(distinctArtists(performances));
	const key = $derived(artists.map((a) => a.id).join(','));

	/** What the API says of each Artist: members and bands, and the kind as edited since the Performances were read. */
	let details = $state.raw<Record<string, Artist>>({});
	let drafts = $state<Record<string, string>>({});
	let error = $state('');
	let busy = $state(false);

	async function load() {
		const loaded = await Promise.all(artists.map((a) => service.get(a.id).catch(() => null)));
		details = Object.fromEntries(loaded.filter((a) => a !== null).map((a) => [a.id, a]));
		drafts = Object.fromEntries(
			Object.values(details).map((a) => [a.id, a.members.map((m) => m.name).join('\n')])
		);
	}

	$effect(() => {
		void key;
		void load();
	});

	function kindOf(id: string, fallback: ArtistKind | null) {
		return details[id] ? (details[id].kind ?? null) : fallback;
	}

	async function change(action: () => Promise<Artist>) {
		busy = true;
		error = '';
		try {
			const updated = await action();
			details = { ...details, [updated.id]: updated };
			drafts = { ...drafts, [updated.id]: updated.members.map((m) => m.name).join('\n') };
		} catch (e) {
			error = e instanceof ArtistError ? e.message : 'The artist could not be saved.';
			await load();
		} finally {
			busy = false;
		}
	}

	function setKind(id: string, value: string) {
		return change(() => service.setKind(id, value === '' ? null : (value as ArtistKind)));
	}

	function saveMembers(event: SubmitEvent, id: string) {
		event.preventDefault();
		return change(() => service.setMembers(id, parseMembers(drafts[id] ?? '')));
	}
</script>

{#if artists.length > 0}
	<section aria-labelledby="recorded-by" class="flex flex-col gap-2">
		<h2 id="recorded-by" class="text-lg font-semibold">Recorded by</h2>
		<ul class="flex flex-col gap-3">
			{#each artists as artist (artist.id)}
				{@const kind = kindOf(artist.id, artist.kind)}
				{@const detail = details[artist.id]}
				<li data-testid="recorded-by-artist" class="flex flex-col gap-1">
					<p>
						<span class="font-medium">{artist.name}</span>
						{#if kind}<span class="text-sm text-ink-muted">({kindLabels[kind]})</span>{/if}
					</p>
					{#if kind === 'band' && detail && detail.members.length > 0}
						<ul class="ml-4 list-disc text-sm text-ink-muted" aria-label="{artist.name} members">
							{#each detail.members as member (member.id)}<li>{member.name}</li>{/each}
						</ul>
					{/if}
					{#if session.account}
						<div class="flex flex-wrap items-end gap-3 text-sm">
							<label class="block font-medium">
								Kind of {artist.name}
								<select
									class={inputClass}
									value={kind ?? ''}
									disabled={busy}
									onchange={(e) => setKind(artist.id, e.currentTarget.value)}
								>
									<option value="">Not classified</option>
									<option value="musician">Musician</option>
									<option value="band">Band</option>
								</select>
							</label>
						</div>
						{#if kind === 'band'}
							<form class="flex flex-col gap-2 text-sm" onsubmit={(e) => saveMembers(e, artist.id)}>
								<label class="block font-medium">
									Members of {artist.name}
									<span class="font-normal text-ink-muted">(one name per line)</span>
									<textarea class={inputClass} rows="3" bind:value={drafts[artist.id]}></textarea>
								</label>
								<div>
									<button type="submit" class="btn-accent" disabled={busy}>Save members</button>
								</div>
							</form>
						{/if}
					{/if}
				</li>
			{/each}
		</ul>
		{#if error}
			<p role="alert" class="text-sm text-danger">{error}</p>
		{/if}
	</section>
{/if}
