import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import type { Track } from '../Tracks.js';
import TrackList from './TrackList.svelte';

/** A track as a file-only upload leaves it: a title, and nothing else yet. */
const sparse: Track = {
	id: 'track-1',
	title: 'knees-up',
	format: 'MP3',
	tags: [],
	owner: { id: 'account-bob', displayName: 'Bob Smith' }
};

beforeEach(() => {
	vi.stubGlobal(
		'fetch',
		vi.fn(async () => Response.json({ status: 'FAILED', peaks: [] }))
	);
});

afterEach(() => {
	vi.restoreAllMocks();
	vi.unstubAllGlobals();
});

describe('a track with no description, artist, album or set', () => {
	it('shows its details without empty lines or a Tunes section', async () => {
		const screen = await render(TrackList, { tracks: [sparse] });

		await page.getByRole('button', { name: 'show track knees-up detail button' }).click();

		const details = screen.container.querySelector('.details');
		expect(details).not.toBeNull();
		expect(details?.textContent?.trim()).toBe('');
		await expect.element(page.getByRole('heading', { name: 'Tunes' })).not.toBeInTheDocument();
	});
});
