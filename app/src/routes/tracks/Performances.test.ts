import { describe, expect, it } from 'vitest';
import { checkPerformance, emptyPerformances, toInput } from './Performances.js';

describe('toInput', () => {
	it('leaves empty fields out', () => {
		expect(toInput({ start: '', key: ' ', notes: '', abc: '' })).toEqual({});
	});
	it('converts the start to seconds and trims text', () => {
		expect(toInput({ start: '1:35.5', key: ' Em ', notes: 'twice', abc: ' X:1\nK:G ' })).toEqual({
			startOffsetSeconds: 95.5,
			key: 'Em',
			notes: 'twice',
			abc: 'X:1\nK:G'
		});
	});
});

describe('checkPerformance', () => {
	it('accepts empty and valid values', () => {
		expect(checkPerformance({ start: '', key: '', notes: '', abc: '' })).toBeUndefined();
		expect(checkPerformance({ start: '2:00', key: 'G', notes: 'x', abc: '' })).toBeUndefined();
	});
	it('refuses a bad start, a long key, long notes and a long transcription', () => {
		expect(checkPerformance({ start: 'soon', key: '', notes: '', abc: '' })).toMatch(/start/);
		expect(checkPerformance({ start: '', key: 'k'.repeat(31), notes: '', abc: '' })).toMatch(/key/);
		expect(checkPerformance({ start: '', key: '', notes: 'n'.repeat(2001), abc: '' })).toMatch(
			/Notes/
		);
		expect(checkPerformance({ start: '', key: '', notes: '', abc: 'a'.repeat(20001) })).toMatch(
			/transcription/
		);
	});
});

describe('emptyPerformances', () => {
	it('gives one distinct form per tune', () => {
		const forms = emptyPerformances(2);
		expect(forms).toHaveLength(2);
		expect(forms[0]).not.toBe(forms[1]);
	});
});
