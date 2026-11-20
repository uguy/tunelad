import { describe, expect, it } from 'vitest';
import { checkFile, MAX_FILE_SIZE, progressFraction, uploadErrorMessage } from './Tracks.js';

describe('checkFile', () => {
	it('accepts each supported extension, whatever its case', () => {
		for (const name of [
			'a.mp3',
			'a.m4a',
			'a.ogg',
			'a.flac',
			'a.wav',
			'A.FLAC',
			'my.live.tune.Mp3'
		]) {
			expect(checkFile({ name, size: 1000 }), name).toBeUndefined();
		}
	});

	it('refuses an unsupported file with the 415 message', () => {
		for (const name of ['notes.txt', 'tune', 'tune.', 'tune.mp3.zip']) {
			expect(checkFile({ name, size: 1000 }), name).toBe(uploadErrorMessage(415));
		}
	});

	it('accepts a file of exactly 100 MB and refuses one byte more with the 413 message', () => {
		expect(MAX_FILE_SIZE).toBe(104_857_600);
		expect(checkFile({ name: 'big.wav', size: MAX_FILE_SIZE })).toBeUndefined();
		expect(checkFile({ name: 'big.wav', size: MAX_FILE_SIZE + 1 })).toBe(uploadErrorMessage(413));
	});
});

describe('uploadErrorMessage', () => {
	it('explains each refusal', () => {
		expect(uploadErrorMessage(413)).toContain('100 MB');
		expect(uploadErrorMessage(415)).toContain('mp3, m4a, ogg, flac or wav');
		expect(uploadErrorMessage(400)).toContain('title, a description');
		expect(uploadErrorMessage(401)).toContain('Log in again');
		expect(uploadErrorMessage(403)).toContain('may not upload');
		expect(uploadErrorMessage(0)).toContain('connection was lost');
		expect(uploadErrorMessage(500)).toBe('The server could not store the track (500). Try again.');
	});
});

describe('progressFraction', () => {
	it('is the share sent so far', () => {
		expect(progressFraction(0, 200)).toBe(0);
		expect(progressFraction(50, 200)).toBe(0.25);
		expect(progressFraction(200, 200)).toBe(1);
	});

	it('stays between 0 and 1', () => {
		expect(progressFraction(300, 200)).toBe(1);
		expect(progressFraction(-1, 200)).toBe(0);
	});

	it('is 0 when the total is unknown', () => {
		expect(progressFraction(50, 0)).toBe(0);
		expect(progressFraction(50, 200, false)).toBe(0);
	});
});
