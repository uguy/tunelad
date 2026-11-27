import { describe, expect, it } from 'vitest';
import TrackService, {
	checkFile,
	MAX_FILE_SIZE,
	progressFraction,
	uploadErrorMessage
} from './Tracks.js';

describe('checkFile', () => {
	it('accepts each supported extension, whatever its case', () => {
		for (const name of [
			'a.mp3',
			'a.m4a',
			'a.ogg',
			'a.flac',
			'a.wav',
			'a.wma',
			'A.WMA',
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
		expect(uploadErrorMessage(415)).toContain('mp3, m4a, ogg, flac, wav or wma');
		expect(uploadErrorMessage(400)).toContain('set or the performances');
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

describe('TrackService.findWaveform', () => {
	const ready = { status: 'READY', peaks: [0.5, 1] };

	it('returns the waveform of the track', async () => {
		let url = '';
		const service = new TrackService(async (input) => {
			url = String(input);
			return Response.json(ready);
		});

		expect(await service.findWaveform('track 1')).toEqual(ready);
		expect(url).toBe('/api/tracks/track%201/waveform');
	});

	it('returns null for an unknown track or a failed request', async () => {
		expect(
			await new TrackService(async () => new Response(null, { status: 404 })).findWaveform('x')
		).toBeNull();
		expect(
			await new TrackService(async () => {
				throw new TypeError('network down');
			}).findWaveform('x')
		).toBeNull();
	});
});

describe('TrackService.findAudio', () => {
	it('returns the state of the track audio', async () => {
		let url = '';
		const service = new TrackService(async (input) => {
			url = String(input);
			return Response.json({ status: 'PENDING' });
		});

		expect(await service.findAudio('track 1')).toEqual({ status: 'PENDING' });
		expect(url).toBe('/api/tracks/track%201/audio');
	});

	it('returns null for an unknown track or a failed request', async () => {
		expect(
			await new TrackService(async () => new Response(null, { status: 404 })).findAudio('x')
		).toBeNull();
		expect(
			await new TrackService(async () => {
				throw new TypeError('network down');
			}).findAudio('x')
		).toBeNull();
	});
});

describe('TrackService.findOne', () => {
	it('returns the track, or null when there is none', async () => {
		const track = { id: 'track-1', title: 'Knees Up' };
		expect(await new TrackService(async () => Response.json(track)).findOne('track-1')).toEqual(
			track
		);
		expect(
			await new TrackService(async () => new Response(null, { status: 404 })).findOne('x')
		).toBeNull();
	});
});

describe('TrackService.tryFindAll', () => {
	const KNEES_UP = { id: 'track-1', title: 'Knees Up', description: 'A reel', format: 'MP3' };

	it('searches with the query and returns the tracks', async () => {
		const requested: string[] = [];
		const service = new TrackService(async (input) => {
			requested.push(String(input));
			return Response.json([KNEES_UP]);
		});

		expect(await service.tryFindAll('rain & tin')).toEqual([KNEES_UP]);
		expect(requested).toEqual(['/api/tracks/search?q=rain%20%26%20tin']);
	});

	it('answers null when the server refuses, where findAll answers no tracks', async () => {
		const service = new TrackService(async () => new Response(null, { status: 500 }));

		expect(await service.tryFindAll('')).toBeNull();
		expect(await service.findAll('')).toEqual([]);
	});

	it('answers null when the server cannot be reached', async () => {
		const service = new TrackService(async () => {
			throw new TypeError('network down');
		});

		expect(await service.tryFindAll('')).toBeNull();
	});
});
