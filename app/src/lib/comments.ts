import { request } from './api.js';

/** What a comment can be about; the first segment of the API path. */
export type CommentSubject = 'tracks' | 'tunes' | 'sets';

/** The longest text a comment may have. */
export const MAX_COMMENT_LENGTH = 2000;

/** A comment as the API returns it. */
export interface Comment {
	id: string;
	text: string;
	authorId: string;
	authorName: string | null;
	createdAt: string;
	editedAt: string | null;
}

/** The server refused a request about comments; `status` is its HTTP status. */
export class CommentError extends Error {
	constructor(readonly status: number) {
		super(commentErrorMessage(status));
	}
}

export function commentErrorMessage(status: number): string {
	switch (status) {
		case 400:
			return `A comment needs a text of at most ${MAX_COMMENT_LENGTH} characters.`;
		case 401:
			return 'Log in to comment.';
		case 403:
			return 'Only the author or an administrator can change this comment.';
		case 404:
			return 'This comment or what it was about no longer exists.';
		default:
			return `The server refused the request (${status}).`;
	}
}

/** Whether the account may edit or delete the comment: its author, or an administrator. */
export function mayChange(
	account: { id: string; admin: boolean } | null,
	comment: Pick<Comment, 'authorId'>
): boolean {
	return !!account && (account.admin || account.id === comment.authorId);
}

async function checked(res: Response) {
	if (!res.ok) throw new CommentError(res.status);
	return res;
}

export default class CommentService {
	constructor(
		private readonly fetch: typeof globalThis.fetch = globalThis.fetch.bind(globalThis)
	) {}

	async list(subject: CommentSubject, id: string): Promise<Comment[]> {
		const res = await checked(
			await request(this.fetch, 'GET', `${subject}/${encodeURIComponent(id)}/comments`)
		);
		return res.json();
	}

	async post(subject: CommentSubject, id: string, text: string): Promise<Comment> {
		const res = await checked(
			await request(this.fetch, 'POST', `${subject}/${encodeURIComponent(id)}/comments`, { text })
		);
		return res.json();
	}

	async edit(id: string, text: string): Promise<Comment> {
		const res = await checked(
			await request(this.fetch, 'PATCH', `comments/${encodeURIComponent(id)}`, { text })
		);
		return res.json();
	}

	async remove(id: string): Promise<void> {
		await checked(await request(this.fetch, 'DELETE', `comments/${encodeURIComponent(id)}`));
	}
}
