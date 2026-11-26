import { describe, expect, it } from 'vitest';
import { Session, session } from './session.svelte.js';

const alice = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };

describe('session.load', () => {
	it('keeps the account the server answers with', async () => {
		await session.load(async () => Response.json(alice));
		expect(session.account).toEqual(alice);
	});

	it('forgets the account when the server answers 401', async () => {
		await session.load(async () => Response.json(alice));
		await session.load(async () => new Response(null, { status: 401 }));
		expect(session.account).toBeNull();
	});

	it('forgets the account when the server cannot be reached', async () => {
		await session.load(async () => Response.json(alice));
		await session.load(async () => {
			throw new TypeError('network down');
		});
		expect(session.account).toBeNull();
	});
});

describe('session.loaded', () => {
	it('is set once the server has answered, whatever the answer', async () => {
		const answered = new Session();
		expect(answered.loaded).toBe(false);
		await answered.load(async () => new Response(null, { status: 401 }));
		expect(answered.loaded).toBe(true);

		const unreachable = new Session();
		await unreachable.load(async () => {
			throw new TypeError('network down');
		});
		expect(unreachable.loaded).toBe(true);
	});
});
