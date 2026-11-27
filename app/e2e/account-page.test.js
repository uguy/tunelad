import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/account, "Account details page".
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const track = (id) => ({ id, title: id, description: '', format: 'MP3', tags: [], owner: ALICE });
const playlist = (id, name) => ({
	id,
	name,
	description: null,
	trackCount: 0,
	updatedAt: '2026-11-27T10:00:00Z'
});

async function loggedIn(page, account, favourites, playlists) {
	await page.route('**/api/me', (route) => route.fulfill({ json: account }));
	await page.route('**/api/me/favourites', (route) => route.fulfill({ json: favourites }));
	await page.route('**/api/playlists', (route) => route.fulfill({ json: playlists }));
}

test('details shown', async ({ page }) => {
	await loggedIn(page, ALICE, ['a', 'b', 'c', 'd'].map(track), [
		playlist('p1', 'One'),
		playlist('p2', 'Two')
	]);
	await page.goto('/account');

	await expect(page.getByTestId('account-name')).toHaveText('Alice Liddell');
	await expect(page.getByTestId('account-role')).toHaveText('Member');
	await expect(page.getByRole('link', { name: /4 favourites/ })).toHaveAttribute(
		'href',
		'/tracks/favourites'
	);
	await expect(page.getByRole('link', { name: /2 playlists/ })).toHaveAttribute(
		'href',
		'/tracks/playlists'
	);
});

test('singular counts', async ({ page }) => {
	await loggedIn(page, ALICE, [track('a')], [playlist('p1', 'One')]);
	await page.goto('/account');

	await expect(page.getByRole('link', { name: /1 favourite$|1 favourite / })).toBeVisible();
	await expect(page.getByRole('link', { name: /1 playlist(?!s)/ })).toBeVisible();
});

test('administrator', async ({ page }) => {
	await loggedIn(page, { ...ALICE, admin: true }, [], []);
	await page.goto('/account');

	await expect(page.getByTestId('account-role')).toHaveText('Administrator');
});

test('log out from the page', async ({ page, context }) => {
	await loggedIn(page, ALICE, [], []);
	await context.addCookies([
		{ name: 'XSRF-TOKEN', value: 'csrf-123', url: 'http://localhost:4173' }
	]);
	let logout;
	await page.route('**/logout', (route) => {
		logout = route.request();
		return route.fulfill({ status: 200, contentType: 'text/html', body: '<p>Logged out</p>' });
	});
	await page.goto('/account');

	await page.getByRole('main').getByRole('button', { name: 'Log out' }).click();

	await expect(page.getByText('Logged out')).toBeVisible();
	expect(logout.method()).toBe('POST');
	expect(logout.postData()).toContain('_csrf=csrf-123');
});

test('anonymous visitor', async ({ page }) => {
	await page.route('**/api/me', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/me/favourites', (route) => route.fulfill({ status: 401 }));
	await page.route('**/api/playlists', (route) => route.fulfill({ status: 401 }));
	await page.goto('/account');

	await expect(page.getByRole('main').getByRole('link', { name: 'Log in' })).toHaveAttribute(
		'href',
		'/login'
	);
	await expect(page.getByTestId('account-name')).toHaveCount(0);
});

test('reached from the account menu', async ({ page }) => {
	await loggedIn(page, ALICE, [], []);
	await page.goto('/');

	await page.getByRole('banner').getByRole('button', { name: 'Alice Liddell' }).click();
	await page.getByRole('menuitem', { name: 'Account' }).click();

	await expect(page).toHaveURL(/\/account$/);
	await expect(page.getByTestId('account-name')).toHaveText('Alice Liddell');
});
