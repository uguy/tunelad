import { expect, test } from '@playwright/test';

// UI-only tests: the REST API is mocked with page.route. Spec: openspec/specs/comment (UI requirement).
const ALICE = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const KESH = { id: 'tune-2', name: 'The Kesh', aliases: [], danceType: 'jig', abc: null };
const comment = (id, authorId, authorName, text) => ({
	id,
	text,
	authorId,
	authorName,
	createdAt: '2026-01-01T10:00:00Z',
	editedAt: null
});

async function mockApi(page, { account = ALICE, existing = [] } = {}) {
	const state = { comments: [...existing] };
	await page.route('**/api/me', (route) =>
		account ? route.fulfill({ json: account }) : route.fulfill({ status: 401 })
	);
	await page.route('**/api/tunes/tune-2', (route) => route.fulfill({ json: KESH }));
	await page.route('**/api/tunes/tune-2/performances', (route) => route.fulfill({ json: [] }));
	await page.route('**/api/tunes/tune-2/comments', (route) => {
		if (route.request().method() === 'POST') {
			const { text } = route.request().postDataJSON();
			const created = comment(`c${state.comments.length + 1}`, ALICE.id, ALICE.displayName, text);
			state.comments.push(created);
			return route.fulfill({ status: 201, json: created });
		}
		return route.fulfill({ json: state.comments });
	});
	await page.route('**/api/comments/*', (route) => {
		const id = new URL(route.request().url()).pathname.split('/')[3];
		state.comments = state.comments.filter((c) => c.id !== id);
		return route.fulfill({ status: 204 });
	});
	return state;
}

test('an anonymous visitor reads comments as text and is invited to log in', async ({ page }) => {
	await mockApi(page, { account: null, existing: [comment('c1', 'x', 'Bob', '<b>hi</b>')] });
	await page.goto('/tunes/tune-2');

	await expect(page.getByTestId('comment')).toContainText('<b>hi</b>');
	await expect(page.getByRole('textbox', { name: 'Add a comment' })).toHaveCount(0);
	await expect(
		page.getByRole('region', { name: /Comments/ }).getByRole('link', { name: 'Log in' })
	).toBeVisible();
});

test('post a comment, then delete it with the keyboard-reachable button', async ({ page }) => {
	await mockApi(page);
	await page.goto('/tunes/tune-2');

	await page.getByRole('textbox', { name: 'Add a comment' }).fill('Lovely tune');
	await page.getByRole('button', { name: 'Post' }).click();
	await expect(page.getByTestId('comment')).toContainText('Lovely tune');
	await expect(page.getByRole('textbox', { name: 'Add a comment' })).toHaveValue('');

	await page.getByRole('button', { name: 'Delete comment by Alice Liddell' }).click();
	await expect(page.getByTestId('comment')).toHaveCount(0);
});

test('edit and delete are offered on own comments only', async ({ page }) => {
	await mockApi(page, {
		existing: [comment('c1', ALICE.id, 'Alice Liddell', 'Mine'), comment('c2', 'x', 'Bob', 'His')]
	});
	await page.goto('/tunes/tune-2');

	await expect(page.getByRole('button', { name: 'Edit comment by Alice Liddell' })).toBeVisible();
	await expect(page.getByRole('button', { name: /comment by Bob/ })).toHaveCount(0);
});
