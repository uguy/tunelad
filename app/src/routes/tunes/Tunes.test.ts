import { describe, expect, it } from 'vitest';
import { parseAliases, parseSourceId, tuneErrorMessage } from './Tunes.js';

describe('parseAliases', () => {
	it('splits on lines and semicolons and drops blanks', () => {
		expect(parseAliases('Kesh Jig; The Kesh \n\n  Kesh  ')).toEqual([
			'Kesh Jig',
			'The Kesh',
			'Kesh'
		]);
	});
	it('is empty for blank text', () => {
		expect(parseAliases('  \n ')).toEqual([]);
	});
});

describe('parseSourceId', () => {
	it('reads a number', () => {
		expect(parseSourceId(' 27 ')).toBe(27);
	});
	it('reads a thesession.org tune URL', () => {
		expect(parseSourceId('https://thesession.org/tunes/27#setting27')).toBe(27);
	});
	it('refuses anything else', () => {
		expect(parseSourceId('cooleys')).toBeNull();
		expect(parseSourceId('')).toBeNull();
	});
});

describe('tuneErrorMessage', () => {
	it('explains the refusals', () => {
		expect(tuneErrorMessage(404)).toMatch(/does not know/);
		expect(tuneErrorMessage(502)).toMatch(/could not be reached/);
		expect(tuneErrorMessage(500)).toMatch(/500/);
	});
});
