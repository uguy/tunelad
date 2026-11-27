import { describe, expect, it } from 'vitest';
import { dropIndex, inLowerHalf, moveItem } from './playlistOrder.js';

describe('moveItem', () => {
	it.each([
		[0, 2, ['b', 'c', 'a']],
		[2, 0, ['c', 'a', 'b']],
		[1, 1, ['a', 'b', 'c']],
		[0, 1, ['b', 'a', 'c']]
	])('moves %i to %i', (from, to, expected) => {
		expect(moveItem(['a', 'b', 'c'], from, to)).toEqual(expected);
	});

	it('leaves the list alone for an index out of range, and never changes its input', () => {
		const items = ['a', 'b'];
		expect(moveItem(items, 0, 5)).toEqual(['a', 'b']);
		expect(moveItem(items, -1, 0)).toEqual(['a', 'b']);
		moveItem(items, 0, 1);
		expect(items).toEqual(['a', 'b']);
	});
});

describe('dropIndex', () => {
	it.each([
		// from, over, after, expected
		[0, 2, true, 2], // below the last row: last
		[0, 1, false, 0], // above the row just below: where it started
		[0, 1, true, 1], // below the row just below: swap
		[2, 0, false, 0], // above the first row: first
		[2, 0, true, 1], // below the first row: second
		[1, 1, true, 1], // on itself: where it was
		[1, 1, false, 1]
	])('from %i over %i (after: %s) ends at %i', (from, over, after, expected) => {
		expect(dropIndex(from, over, after)).toBe(expected);
	});

	it('gives the order the drop shows', () => {
		expect(moveItem(['a', 'b', 'c'], 0, dropIndex(0, 2, true))).toEqual(['b', 'c', 'a']);
		expect(moveItem(['a', 'b', 'c'], 2, dropIndex(2, 0, false))).toEqual(['c', 'a', 'b']);
	});
});

describe('inLowerHalf', () => {
	it('tells the halves of a row', () => {
		expect(inLowerHalf(110, { top: 100, height: 40 })).toBe(false);
		expect(inLowerHalf(130, { top: 100, height: 40 })).toBe(true);
	});
});
