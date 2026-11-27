import { describe, expect, it } from 'vitest';
import { mayChange } from './comments.js';

const alice = { id: 'a', admin: false };
const admin = { id: 'z', admin: true };

describe('mayChange', () => {
	it('lets the author and an administrator change a comment, nobody else', () => {
		expect(mayChange(alice, { authorId: 'a' })).toBe(true);
		expect(mayChange(admin, { authorId: 'a' })).toBe(true);
		expect(mayChange({ id: 'b', admin: false }, { authorId: 'a' })).toBe(false);
		expect(mayChange(null, { authorId: 'a' })).toBe(false);
	});
});
