import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import Comments from './Comments.svelte';
import CommentService, { CommentError, commentErrorMessage } from './comments.js';
import { session } from './session.svelte.js';

const comment = (id: string, authorId: string, text: string) => ({
	id,
	text,
	authorId,
	authorName: authorId === 'a' ? 'Alice' : 'Bob',
	createdAt: '2026-01-01T10:00:00Z',
	editedAt: null
});

let comments: ReturnType<typeof comment>[];

beforeEach(() => {
	comments = [comment('c1', 'a', 'Lovely flute'), comment('c2', 'b', '<b>bold</b>')];
	vi.stubGlobal(
		'fetch',
		vi.fn(async (url: string, init?: RequestInit) => {
			if (init?.method === 'DELETE') {
				comments = comments.filter((c) => url.endsWith(`/${c.id}`) === false);
				return new Response(null, { status: 204 });
			}
			if (init?.method === 'POST') {
				const body = JSON.parse(String(init.body));
				comments.push(comment('c3', 'a', body.text));
				return new Response('{}', { status: 201 });
			}
			return Response.json(comments);
		})
	);
});

afterEach(() => {
	vi.unstubAllGlobals();
	session.account = null;
	session.loaded = false;
});

describe('Comments', () => {
	it('shows comments to an anonymous visitor, as text, with an invitation to log in', async () => {
		session.loaded = true;
		const screen = await render(Comments, { subject: 'tunes', id: 't1' });

		await expect.element(page.getByText('Lovely flute')).toBeVisible();
		await expect.element(page.getByText('<b>bold</b>')).toBeVisible();
		expect(screen.container.querySelector('textarea')).toBeNull();
		await expect.element(page.getByRole('link', { name: 'Log in' })).toBeVisible();
	});

	it('offers edit and delete on own comments only', async () => {
		session.account = { id: 'a', displayName: 'Alice', admin: false };
		session.loaded = true;
		await render(Comments, { subject: 'tunes', id: 't1' });

		await expect
			.element(page.getByRole('button', { name: /Delete comment by Alice/ }))
			.toBeVisible();
		await expect
			.element(page.getByRole('button', { name: /Delete comment by Bob/ }))
			.not.toBeInTheDocument();
	});

	it('offers delete on every comment to an administrator', async () => {
		session.account = { id: 'z', displayName: 'Admin', admin: true };
		session.loaded = true;
		await render(Comments, { subject: 'tunes', id: 't1' });

		await expect.element(page.getByRole('button', { name: /Delete comment by Bob/ })).toBeVisible();
	});

	it('posts a comment, lists it and clears the form', async () => {
		session.account = { id: 'a', displayName: 'Alice', admin: false };
		session.loaded = true;
		await render(Comments, { subject: 'tracks', id: 't1' });

		await page.getByRole('textbox', { name: 'Add a comment' }).fill('Great');
		await page.getByRole('button', { name: 'Post' }).click();

		await expect.element(page.getByText('Great')).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Add a comment' })).toHaveValue('');
	});
});

describe('CommentService', () => {
	it('posts to the subject, with the text only', async () => {
		const fetch = vi.fn().mockResolvedValue(new Response('{"id":"c1"}', { status: 201 }));

		await new CommentService(fetch).post('tunes', 't 1', 'Hello');

		expect(fetch).toHaveBeenCalledWith(
			'/api/tunes/t%201/comments',
			expect.objectContaining({ method: 'POST', body: '{"text":"Hello"}' })
		);
	});

	it('turns a refusal into an error that says what happened', async () => {
		const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 403 }));

		const refused = new CommentService(fetch).remove('c1');

		await expect(refused).rejects.toBeInstanceOf(CommentError);
		await expect(refused).rejects.toThrow(commentErrorMessage(403));
	});
});
