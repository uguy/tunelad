import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FakeEventSource, fakeLive } from './live.testing.js';

const sources = () => FakeEventSource.instances;

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('open', () => {
	it('connects to the live stream, once', () => {
		const live = fakeLive();

		live.open();
		live.open();

		expect(sources()).toHaveLength(1);
		expect(sources()[0].url).toBe('/api/live');
	});

	it('is connected once the stream is open, and not after it fails', () => {
		const live = fakeLive();
		live.open();
		expect(live.connected).toBe(false);

		sources()[0].open();
		expect(live.connected).toBe(true);

		sources()[0].fail();
		expect(live.connected).toBe(false);
	});
});

describe('on', () => {
	it('calls the handlers of an event type with the type and the id', () => {
		const live = fakeLive();
		const indexed = vi.fn();
		const waveform = vi.fn();
		live.on('track.indexed', indexed);
		live.on('waveform.computed', waveform);
		live.open();

		sources()[0].emit('track.indexed', { type: 'track.indexed', id: '42' });

		expect(indexed).toHaveBeenCalledExactlyOnceWith({ type: 'track.indexed', id: '42' });
		expect(waveform).not.toHaveBeenCalled();
	});

	it('listens once the stream is open too', () => {
		const live = fakeLive();
		live.open();
		const handler = vi.fn();

		live.on('track.unindexed', handler);
		sources()[0].emit('track.unindexed', { type: 'track.unindexed', id: '7' });

		expect(handler).toHaveBeenCalledExactlyOnceWith({ type: 'track.unindexed', id: '7' });
	});

	it('calls every handler of a type', () => {
		const live = fakeLive();
		const first = vi.fn();
		const second = vi.fn();
		live.on('track.indexed', first);
		live.on('track.indexed', second);
		live.open();

		sources()[0].emit('track.indexed', { type: 'track.indexed', id: '1' });

		expect(first).toHaveBeenCalledOnce();
		expect(second).toHaveBeenCalledOnce();
	});

	it('stops calling a handler that was removed', () => {
		const live = fakeLive();
		const handler = vi.fn();
		const off = live.on('track.indexed', handler);
		live.open();

		off();
		sources()[0].emit('track.indexed', { type: 'track.indexed', id: '1' });

		expect(handler).not.toHaveBeenCalled();
	});

	it('gives no id to an event that has none', () => {
		const live = fakeLive();
		const handler = vi.fn();
		live.on('favourites.changed', handler);
		live.open();

		sources()[0].emit('favourites.changed', { type: 'favourites.changed' });

		expect(handler).toHaveBeenCalledExactlyOnceWith({ type: 'favourites.changed', id: undefined });
	});

	it('ignores an event that is not JSON', () => {
		const live = fakeLive();
		const handler = vi.fn();
		live.on('track.indexed', handler);
		live.open();

		sources()[0].emit('track.indexed', 'not json {');

		expect(handler).not.toHaveBeenCalled();
	});

	it('goes on with the other handlers when one fails', () => {
		const live = fakeLive();
		const error = vi.spyOn(console, 'error').mockImplementation(() => {});
		const after = vi.fn();
		live.on('track.indexed', () => {
			throw new Error('boom');
		});
		live.on('track.indexed', after);
		live.open();

		sources()[0].emit('track.indexed', { type: 'track.indexed', id: '1' });

		expect(after).toHaveBeenCalledOnce();
		expect(error).toHaveBeenCalledOnce();
		error.mockRestore();
	});
});

describe('onReconnect', () => {
	it('is not called when the stream opens for the first time', () => {
		const live = fakeLive();
		const reconnected = vi.fn();
		live.onReconnect(reconnected);

		live.open();
		sources()[0].open();

		expect(reconnected).not.toHaveBeenCalled();
	});

	it('is called when the browser reconnects after a failure, once', () => {
		const live = fakeLive();
		const reconnected = vi.fn();
		live.onReconnect(reconnected);
		live.open();
		sources()[0].open();

		sources()[0].fail();
		sources()[0].open();
		sources()[0].open();

		expect(reconnected).toHaveBeenCalledOnce();
	});

	it('is not called for a handler that was removed', () => {
		const live = fakeLive();
		const reconnected = vi.fn();
		const off = live.onReconnect(reconnected);
		live.open();
		sources()[0].open();
		off();

		sources()[0].fail();
		sources()[0].open();

		expect(reconnected).not.toHaveBeenCalled();
	});

	it('opens a new stream when the browser gave up on the first, and then reports the reconnection', () => {
		const live = fakeLive();
		const handler = vi.fn();
		const reconnected = vi.fn();
		live.on('track.indexed', handler);
		live.onReconnect(reconnected);
		live.open();
		sources()[0].open();

		// A refused connection or an error response (a server that is restarting) closes the stream for good
		sources()[0].fail(true);
		expect(sources()).toHaveLength(1);
		vi.advanceTimersByTime(5000);

		expect(sources()).toHaveLength(2);
		expect(sources()[0].closed).toBe(true);
		sources()[1].open();
		expect(reconnected).toHaveBeenCalledOnce();
		sources()[1].emit('track.indexed', { type: 'track.indexed', id: '9' });
		expect(handler).toHaveBeenCalledExactlyOnceWith({ type: 'track.indexed', id: '9' });
	});

	it('does not open a new stream while the browser is still trying', () => {
		const live = fakeLive();
		live.open();

		sources()[0].fail();
		vi.advanceTimersByTime(60_000);

		expect(sources()).toHaveLength(1);
	});
});

describe('close', () => {
	it('closes the stream', () => {
		const live = fakeLive();
		live.open();
		sources()[0].open();

		live.close();

		expect(sources()[0].closed).toBe(true);
		expect(live.connected).toBe(false);
	});

	it('stops trying to open the stream again', () => {
		const live = fakeLive();
		live.open();
		sources()[0].fail(true);

		live.close();
		vi.advanceTimersByTime(60_000);

		expect(sources()).toHaveLength(1);
	});

	it('lets the stream be opened again, with the handlers that were added', () => {
		const live = fakeLive();
		const handler = vi.fn();
		live.on('track.indexed', handler);
		live.open();
		live.close();

		live.open();
		sources()[1].emit('track.indexed', { type: 'track.indexed', id: '3' });

		expect(sources()).toHaveLength(2);
		expect(handler).toHaveBeenCalledOnce();
	});
});
