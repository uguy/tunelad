import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import LoginPage from './+page.svelte';

// The page only reads `data.providers`, which its load function fills from the API
const renderPage = (providers: { id: string; name: string }[]) =>
	render(LoginPage, { data: { providers } } as never);

describe('login page', () => {
	it('offers a button for each provider, which starts its login', async () => {
		const screen = await renderPage([
			{ id: 'keycloak', name: 'Keycloak' },
			{ id: 'google', name: 'Google' }
		]);

		await expect
			.element(screen.getByRole('link', { name: 'Continue with Keycloak' }))
			.toHaveAttribute('href', '/oauth2/authorization/keycloak');
		await expect
			.element(screen.getByRole('link', { name: 'Continue with Google' }))
			.toHaveAttribute('href', '/oauth2/authorization/google');
	});

	it('says so when no provider is configured', async () => {
		const screen = await renderPage([]);

		await expect.element(screen.getByText('No login provider is configured.')).toBeVisible();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
	});
});
