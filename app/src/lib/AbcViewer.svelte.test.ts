import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { player } from './player.svelte.js';
import AbcViewer from './AbcViewer.svelte';

const synthInstance = vi.hoisted(() => ({
	init: vi.fn(async () => {}),
	prime: vi.fn(async () => {}),
	start: vi.fn(),
	stop: vi.fn()
}));
const renderAbc = vi.hoisted(() =>
	vi.fn((el: HTMLElement) => {
		el.innerHTML = '<svg data-testid="sheet-svg"></svg>';
		return [{ millisecondsPerMeasure: () => 2000 }];
	})
);

vi.mock('abcjs', () => ({
	default: {
		renderAbc,
		synth: {
			supportsAudio: () => true,
			CreateSynth: class {
				init = synthInstance.init;
				prime = synthInstance.prime;
				start = synthInstance.start;
				stop = synthInstance.stop;
			}
		}
	}
}));

const ABC = 'X:1\nT:Cooley\nM:4/4\nK:Edor\nEBBA|';

beforeEach(() => {
	vi.clearAllMocks();
	player.paused = true;
});

describe('AbcViewer', () => {
	it('shows the sheet music first, and switches to the ABC text', async () => {
		render(AbcViewer, { abc: ABC, title: "Cooley's" });

		await expect.element(page.getByTestId('sheet-svg')).toBeInTheDocument();
		await page.getByRole('button', { name: 'ABC text' }).click();

		await expect.element(page.getByText('EBBA|', { exact: false })).toBeVisible();
		await expect.element(page.getByTestId('sheet-svg')).not.toBeInTheDocument();
		await expect
			.element(page.getByRole('button', { name: 'ABC text' }))
			.toHaveAttribute('aria-pressed', 'true');
	});

	it('says so, with no switch and no Play, when there is no transcription', async () => {
		render(AbcViewer, { abc: null, title: 'The Kesh' });

		await expect.element(page.getByText(/no transcription/)).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Play' })).not.toBeInTheDocument();
		await expect.element(page.getByRole('button', { name: 'ABC text' })).not.toBeInTheDocument();
	});

	it('plays and stops, pausing the track player', async () => {
		player.paused = false;
		render(AbcViewer, { abc: ABC, title: "Cooley's" });

		await page.getByRole('button', { name: 'Play' }).click();

		await expect.element(page.getByRole('button', { name: 'Stop' })).toBeVisible();
		expect(player.paused).toBe(true);
		expect(synthInstance.start).toHaveBeenCalled();

		await page.getByRole('button', { name: 'Stop' }).click();
		await expect.element(page.getByRole('button', { name: 'Play' })).toBeVisible();
		expect(synthInstance.stop).toHaveBeenCalled();
	});

	it('tells the user when the sound cannot start', async () => {
		synthInstance.prime.mockRejectedValueOnce(new Error('no soundfont'));
		render(AbcViewer, { abc: ABC, title: "Cooley's" });

		await page.getByRole('button', { name: 'Play' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent(
				'This tune could not be played here. Check that your browser allows audio.'
			);
		await expect.element(page.getByRole('button', { name: 'Play' })).toBeVisible();
	});
});
