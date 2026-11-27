import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import PlayerBar from './PlayerBar.svelte';
import { player } from './player.svelte.js';

beforeEach(() => {
	// A test page has had no click: the browser would refuse the autoplay the bound `paused` asks for
	vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
	player.source = null;
	player.track = null;
});

afterEach(() => {
	vi.restoreAllMocks();
	player.source = null;
	player.track = null;
});

describe('PlayerBar', () => {
	it('says in text when the loaded audio cannot be played yet', async () => {
		const screen = await render(PlayerBar);
		player.play('track-1', 0, { id: 'track-1', title: 'Touching Cloth' });
		await expect.element(page.getByTestId('player-title')).toHaveTextContent('Touching Cloth');
		expect(screen.container.querySelector('[data-testid="player-unplayable"]')).toBeNull();

		// What the browser does when the server answers 503 while the audio is being converted
		document.querySelector('audio')!.dispatchEvent(new Event('error'));

		await expect
			.element(page.getByRole('status').filter({ hasText: 'This audio cannot be played yet' }))
			.toBeVisible();
	});

	it('forgets the message when another track is loaded', async () => {
		const screen = await render(PlayerBar);
		player.play('track-1', 0, { id: 'track-1', title: 'Touching Cloth' });
		await expect.element(page.getByTestId('player-title')).toBeVisible();
		document.querySelector('audio')!.dispatchEvent(new Event('error'));
		await expect.element(page.getByTestId('player-unplayable')).toBeVisible();

		player.play('track-2', 0, { id: 'track-2', title: 'Knees Up' });

		await expect.element(page.getByTestId('player-title')).toHaveTextContent('Knees Up');
		expect(screen.container.querySelector('[data-testid="player-unplayable"]')).toBeNull();
	});
});
