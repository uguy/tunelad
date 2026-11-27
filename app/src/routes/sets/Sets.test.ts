import { describe, expect, it } from 'vitest';
import { setErrorMessage, setTitle } from './Sets.js';

describe('setTitle', () => {
	it('lists the tunes in order', () => {
		expect(
			setTitle({
				id: 's',
				tunes: [
					{ id: '1', name: "Cooley's", danceType: 'reel' },
					{ id: '2', name: 'The Kesh', danceType: 'jig' }
				]
			})
		).toBe("Cooley's, The Kesh");
	});
});

describe('setErrorMessage', () => {
	it('explains the refusals', () => {
		expect(setErrorMessage(400)).toMatch(/one to 20/);
		expect(setErrorMessage(401)).toMatch(/Log in/);
		expect(setErrorMessage(500)).toMatch(/500/);
	});
});
