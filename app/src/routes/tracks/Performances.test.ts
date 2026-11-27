import { describe, expect, it } from 'vitest';
import { checkPerformance, emptyPerformances, toInput } from './Performances.js';

describe('toInput', () => {
	it('leaves empty fields out', () => {
		expect(toInput({ start: '', key: ' ', notes: '' })).toEqual({});
	});
	it('converts the start to seconds and trims text', () => {
		expect(toInput({ start: '1:35.5', key: ' Em ', notes: 'twice' })).toEqual({
			startOffsetSeconds: 95.5,
			key: 'Em',
			notes: 'twice'
		});
	});
});

describe('checkPerformance', () => {
	it('accepts empty and valid values', () => {
		expect(checkPerformance({ start: '', key: '', notes: '' })).toBeUndefined();
		expect(checkPerformance({ start: '2:00', key: 'G', notes: 'x' })).toBeUndefined();
	});
	it('refuses a bad start, a long key and long notes', () => {
		expect(checkPerformance({ start: 'soon', key: '', notes: '' })).toMatch(/start/);
		expect(checkPerformance({ start: '', key: 'k'.repeat(31), notes: '' })).toMatch(/key/);
		expect(checkPerformance({ start: '', key: '', notes: 'n'.repeat(2001) })).toMatch(/Notes/);
	});
});

describe('emptyPerformances', () => {
	it('gives one distinct form per tune', () => {
		const forms = emptyPerformances(2);
		expect(forms).toHaveLength(2);
		expect(forms[0]).not.toBe(forms[1]);
	});
});
