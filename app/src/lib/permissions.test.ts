import { describe, expect, it } from 'vitest';
import { canChange } from './permissions.js';

const alice = { id: 'account-alice', displayName: 'Alice Liddell', admin: false };
const admin = { id: 'account-admin', displayName: 'Tune Lad Admin', admin: true };
const alicesTrack = { owner: { id: 'account-alice' } };
const bobsTrack = { owner: { id: 'account-bob' } };

describe('canChange', () => {
	it('lets nobody anonymous change a track', () => {
		expect(canChange(null, alicesTrack)).toBe(false);
	});

	it('lets the owner change their track', () => {
		expect(canChange(alice, alicesTrack)).toBe(true);
	});

	it("does not let another user change someone else's track", () => {
		expect(canChange(alice, bobsTrack)).toBe(false);
	});

	it('lets an administrator change any track', () => {
		expect(canChange(admin, bobsTrack)).toBe(true);
	});

	it('lets only an administrator change a track without an owner', () => {
		expect(canChange(alice, { owner: null })).toBe(false);
		expect(canChange(alice, {})).toBe(false);
		expect(canChange(admin, {})).toBe(true);
	});
});
