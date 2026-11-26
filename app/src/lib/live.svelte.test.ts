import { flushSync } from 'svelte';
import { describe, expect, it } from 'vitest';
import { FakeEventSource, fakeLive } from './live.testing.js';

const sources = () => FakeEventSource.instances;

describe('follow', () => {
	it('opens the stream once it is known who is using it, and closes it when it stops following', () => {
		let who = $state<string | null | undefined>(undefined);
		const live = fakeLive();

		const stop = live.follow(() => who);
		flushSync();
		expect(sources()).toHaveLength(0);

		who = null;
		flushSync();
		expect(sources()).toHaveLength(1);

		stop();
		expect(sources()[0].closed).toBe(true);
	});

	it('reopens the stream when the account changes, on login and on logout', () => {
		let who = $state<string | null | undefined>(null);
		const live = fakeLive();
		const stop = live.follow(() => who);
		flushSync();
		expect(sources()).toHaveLength(1);

		who = 'account-alice';
		flushSync();
		expect(sources()).toHaveLength(2);
		expect(sources()[0].closed).toBe(true);
		expect(sources()[1].closed).toBe(false);

		who = null;
		flushSync();
		expect(sources()).toHaveLength(3);
		expect(sources()[1].closed).toBe(true);
		stop();
	});

	it('keeps the same stream while the account does not change', () => {
		let who = $state<string | null | undefined>('account-alice');
		const live = fakeLive();
		const stop = live.follow(() => who);
		flushSync();

		who = 'account-alice';
		flushSync();

		expect(sources()).toHaveLength(1);
		stop();
	});
});
