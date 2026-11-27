import { describe, expect, it } from 'vitest';
import { distinctArtists, parseMembers } from './artists.js';

describe('distinctArtists', () => {
	it('lists each Artist once, in order of first appearance', () => {
		expect(
			distinctArtists([
				{ artistId: 'a', artist: 'Altan', artistKind: 'band' },
				{ artistId: 'b', artist: 'Paul Brock', artistKind: 'musician' },
				{ artistId: 'a', artist: 'Altan', artistKind: 'band' }
			])
		).toEqual([
			{ id: 'a', name: 'Altan', kind: 'band' },
			{ id: 'b', name: 'Paul Brock', kind: 'musician' }
		]);
	});
	it('skips Performances without an Artist and keeps unclassified ones', () => {
		expect(
			distinctArtists([
				{ artistId: null, artist: null },
				{ artistId: 'c', artist: 'Dervish' }
			])
		).toEqual([{ id: 'c', name: 'Dervish', kind: null }]);
		expect(distinctArtists([{}])).toEqual([]);
	});
});

describe('parseMembers', () => {
	it('splits on lines and semicolons and drops blanks', () => {
		expect(parseMembers('Mairéad;  Paul Brock\n\n ')).toEqual(['Mairéad', 'Paul Brock']);
	});
});
