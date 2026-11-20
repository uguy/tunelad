import { describe, expect, it } from 'vitest';
import { artworkGradient, formatTime } from './artwork.js';

describe('artworkGradient', () => {
	it('is the same for the same track, and differs between tracks', () => {
		expect(artworkGradient('track-1')).toBe(artworkGradient('track-1'));
		expect(artworkGradient('track-1')).not.toBe(artworkGradient('track-2'));
		expect(artworkGradient('track-1')).toMatch(/^linear-gradient\(135deg, hsl\(\d+ /);
	});
});

describe('formatTime', () => {
	it('formats minutes and seconds, and hours when needed', () => {
		expect(formatTime(0)).toBe('0:00');
		expect(formatTime(95.7)).toBe('1:35');
		expect(formatTime(3725)).toBe('1:02:05');
	});

	it('shows 0:00 for an unknown time', () => {
		expect(formatTime(NaN)).toBe('0:00');
		expect(formatTime(Infinity)).toBe('0:00');
		expect(formatTime(-1)).toBe('0:00');
	});
});
