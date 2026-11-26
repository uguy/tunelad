import type { LiveState } from './live.svelte.js';

/** How long to wait for more events before refreshing (milliseconds): a burst of events makes one request. */
export const REFRESH_DELAY = 300;

/**
 * Calls `refresh` when live events of the types arrive, and when the live connection is back after a failure (events
 * may have been missed). A burst of events makes one call, `delay` after the last of them. Returns the function that
 * stops it, for when the page goes away.
 */
export function refreshOnLive(
	live: Pick<LiveState, 'on' | 'onReconnect'>,
	types: string[],
	refresh: () => void,
	delay = REFRESH_DELAY
): () => void {
	let timer: ReturnType<typeof setTimeout> | undefined;
	const schedule = () => {
		clearTimeout(timer);
		timer = setTimeout(refresh, delay);
	};
	const stops = [...types.map((type) => live.on(type, schedule)), live.onReconnect(schedule)];
	return () => {
		clearTimeout(timer);
		stops.forEach((stop) => stop());
	};
}
