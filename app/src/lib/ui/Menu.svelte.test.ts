import { describe, expect, it } from 'vitest';
import { page, userEvent } from 'vitest/browser';
import { render } from 'vitest-browser-svelte';
import MenuHarness from './MenuHarness.svelte';

describe('Menu', () => {
	it('opens from its button, with the right ARIA state', async () => {
		render(MenuHarness);
		const button = page.getByRole('button', { name: 'Open' });
		await expect.element(button).toHaveAttribute('aria-expanded', 'false');
		await expect.element(button).toHaveAttribute('aria-haspopup', 'menu');

		await button.click();

		await expect.element(button).toHaveAttribute('aria-expanded', 'true');
		await expect.element(page.getByRole('menu')).toBeVisible();
		await expect.element(page.getByRole('menuitem', { name: 'First' })).toHaveFocus();
	});

	it('moves through the items with the arrow keys, wrapping around', async () => {
		render(MenuHarness);
		await page.getByRole('button', { name: 'Open' }).click();

		await userEvent.keyboard('{ArrowDown}');
		await expect.element(page.getByRole('menuitem', { name: 'Second' })).toHaveFocus();
		await userEvent.keyboard('{ArrowDown}');
		await expect.element(page.getByRole('menuitem', { name: 'First' })).toHaveFocus();
		await userEvent.keyboard('{ArrowUp}');
		await expect.element(page.getByRole('menuitem', { name: 'Second' })).toHaveFocus();
	});

	it('closes on Escape and gives the focus back to its button', async () => {
		render(MenuHarness);
		const button = page.getByRole('button', { name: 'Open' });
		await button.click();

		await userEvent.keyboard('{Escape}');

		await expect.element(page.getByRole('menu')).not.toBeInTheDocument();
		await expect.element(button).toHaveFocus();
	});

	it('closes when an item is chosen', async () => {
		render(MenuHarness);
		await page.getByRole('button', { name: 'Open' }).click();

		await page.getByRole('menuitem', { name: 'Second' }).click();

		await expect.element(page.getByRole('menu')).not.toBeInTheDocument();
	});

	it('closes on a click outside', async () => {
		render(MenuHarness);
		await page.getByRole('button', { name: 'Open' }).click();

		await page.getByText('outside').click();

		await expect.element(page.getByRole('menu')).not.toBeInTheDocument();
	});
});
