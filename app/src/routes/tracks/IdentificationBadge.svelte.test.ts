import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import IdentificationBadge from './IdentificationBadge.svelte';

describe('IdentificationBadge', () => {
	it.each([
		['pending', 'Not identified'],
		['identified', 'Identified from the audio'],
		['corrected', 'Corrected']
	] as const)('says %s in words', async (state, text) => {
		const screen = await render(IdentificationBadge, { state });

		await expect.element(screen.getByText(text)).toBeVisible();
		expect(screen.container.querySelector(`[data-identification="${state}"]`)).not.toBeNull();
	});

	it('draws nothing without a state', async () => {
		const screen = await render(IdentificationBadge, {});

		expect(screen.container.querySelector('[data-identification]')).toBeNull();
	});
});
