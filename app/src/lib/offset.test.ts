import { describe, expect, it } from 'vitest';
import { formatOffset, parseOffset } from './offset.js';

describe('formatOffset', () => {
	it('writes minutes and seconds', () => {
		expect(formatOffset(0)).toBe('0:00');
		expect(formatOffset(95.5)).toBe('1:35.5');
		expect(formatOffset(95)).toBe('1:35');
	});
	it('adds hours from an hour on', () => {
		expect(formatOffset(3723)).toBe('1:02:03');
	});
});

describe('parseOffset', () => {
	it('reads seconds, minutes and hours', () => {
		expect(parseOffset('95.5')).toBe(95.5);
		expect(parseOffset('1:35')).toBe(95);
		expect(parseOffset('1:35.5')).toBe(95.5);
		expect(parseOffset('1:02:03')).toBe(3723);
	});
	it('takes empty text for no offset', () => {
		expect(parseOffset('  ')).toBeNull();
	});
	it('refuses what is not an offset', () => {
		for (const text of ['abc', '-1', '1:75', '1:99:00', '100000', '1:', '1,5']) {
			expect(parseOffset(text), text).toBeNaN();
		}
	});
});
