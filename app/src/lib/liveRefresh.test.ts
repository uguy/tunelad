import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { REFRESH_DELAY, refreshOnLive } from './liveRefresh.js';
import { FakeEventSource, fakeLive } from './live.testing.js';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

function started() {
	const live = fakeLive();
	const refresh = vi.fn();
	const stop = refreshOnLive(live, ['track.indexed', 'track.unindexed'], refresh);
	live.open();
	const source = FakeEventSource.instances[0];
	source.open();
	return { source, refresh, stop };
}

describe('refreshOnLive', () => {
	it('refreshes once the delay has passed since an event', () => {
		const { source, refresh } = started();

		source.emit('track.indexed', { type: 'track.indexed', id: '1' });
		expect(refresh).not.toHaveBeenCalled();
		vi.advanceTimersByTime(REFRESH_DELAY);

		expect(refresh).toHaveBeenCalledOnce();
	});

	it('makes one refresh of a burst of events, of either type', () => {
		const { source, refresh } = started();

		for (let i = 0; i < 20; i++) {
			source.emit(i % 2 ? 'track.indexed' : 'track.unindexed', { type: 'x', id: String(i) });
			vi.advanceTimersByTime(REFRESH_DELAY / 3);
		}
		vi.advanceTimersByTime(REFRESH_DELAY);

		expect(refresh).toHaveBeenCalledOnce();
	});

	it('refreshes again for events that come later', () => {
		const { source, refresh } = started();

		source.emit('track.indexed', { type: 'track.indexed', id: '1' });
		vi.advanceTimersByTime(REFRESH_DELAY);
		source.emit('track.unindexed', { type: 'track.unindexed', id: '1' });
		vi.advanceTimersByTime(REFRESH_DELAY);

		expect(refresh).toHaveBeenCalledTimes(2);
	});

	it('ignores events of other types', () => {
		const { source, refresh } = started();

		source.emit('waveform.computed', { type: 'waveform.computed', id: '1' });
		vi.advanceTimersByTime(REFRESH_DELAY * 2);

		expect(refresh).not.toHaveBeenCalled();
	});

	it('refreshes when the connection is back after a failure', () => {
		const { source, refresh } = started();

		source.fail();
		source.open();
		vi.advanceTimersByTime(REFRESH_DELAY);

		expect(refresh).toHaveBeenCalledOnce();
	});

	it('does nothing after it was stopped, not even for an event that was waiting', () => {
		const { source, refresh, stop } = started();
		source.emit('track.indexed', { type: 'track.indexed', id: '1' });

		stop();
		source.emit('track.indexed', { type: 'track.indexed', id: '2' });
		vi.advanceTimersByTime(REFRESH_DELAY * 2);

		expect(refresh).not.toHaveBeenCalled();
	});
});
