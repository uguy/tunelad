import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import Waveform from './Waveform.svelte';

const PEAKS = Array.from({ length: 200 }, (_, i) => (i % 10) / 10);

describe('Waveform', () => {
	it('reports the share of its width that was clicked', async () => {
		const onseek = vi.fn();
		const screen = await render(Waveform, { peaks: PEAKS, label: 'Knees Up', onseek });
		const slider = screen.getByRole('slider', { name: 'seek Knees Up' });
		const box = slider.element().getBoundingClientRect();

		await slider.click({ position: { x: box.width * 0.75, y: box.height / 2 } });

		expect(onseek).toHaveBeenCalledOnce();
		expect(onseek.mock.calls[0][0]).toBeCloseTo(0.75, 1);
	});

	it('highlights the played share of its bars', async () => {
		const screen = await render(Waveform, { peaks: PEAKS, label: 'Knees Up', progress: 0.75 });

		const clip = screen.container.querySelector('[data-testid="played-clip"]');
		expect(clip?.getAttribute('width')).toBe('150');
		await expect
			.element(screen.getByRole('slider', { name: 'seek Knees Up' }))
			.toHaveAttribute('aria-valuenow', '75');
		// One bar and its reflection per peak, drawn once unplayed and once played
		expect(screen.container.querySelectorAll('rect:not([data-testid])')).toHaveLength(800);
	});

	it('draws a flat placeholder without peaks, which seeks the same way', async () => {
		const onseek = vi.fn();
		const screen = await render(Waveform, { peaks: null, label: 'Reel', onseek });

		await expect.element(page.getByTestId('waveform-placeholder')).toBeVisible();
		await screen.getByRole('slider', { name: 'seek Reel' }).click({ position: { x: 1, y: 10 } });

		expect(onseek).toHaveBeenCalledOnce();
		expect(onseek.mock.calls[0][0]).toBeLessThan(0.05);
	});

	it('seeks by 5% with the arrow keys', async () => {
		const onseek = vi.fn();
		const screen = await render(Waveform, { peaks: PEAKS, label: 'Reel', progress: 0.5, onseek });
		const slider = screen.getByRole('slider', { name: 'seek Reel' });

		slider
			.element()
			.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));

		expect(onseek).toHaveBeenCalledWith(0.55);
	});
});
