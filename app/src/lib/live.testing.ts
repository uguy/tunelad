import { LiveState } from './live.svelte.js';

/**
 * A stand-in for the browser's `EventSource`, which tests drive by hand: `open()`, `fail()` and `emit()` do what the
 * network would. Test support only: nothing in the app imports it.
 */
export class FakeEventSource {
	static instances: FakeEventSource[] = [];

	/** 0 connecting, 1 open, 2 closed, as `EventSource` has it. */
	readyState = 0;
	closed = false;
	onopen: (() => void) | null = null;
	onerror: (() => void) | null = null;
	readonly #listeners = new Map<string, ((message: { data: string }) => void)[]>();

	constructor(readonly url: string) {
		FakeEventSource.instances.push(this);
	}

	addEventListener(type: string, listener: (message: { data: string }) => void) {
		this.#listeners.set(type, [...(this.#listeners.get(type) ?? []), listener]);
	}

	close() {
		this.readyState = 2;
		this.closed = true;
	}

	/** The connection is open. */
	open() {
		this.readyState = 1;
		this.onopen?.();
	}

	/** The connection failed: the browser keeps trying, unless it gave up and closed it. */
	fail(gaveUp = false) {
		this.readyState = gaveUp ? 2 : 0;
		this.onerror?.();
	}

	/** The server sent an event; an object is sent as JSON. */
	emit(type: string, data: unknown) {
		const text = typeof data === 'string' ? data : JSON.stringify(data);
		for (const listener of this.#listeners.get(type) ?? []) listener({ data: text });
	}

	/** The listeners for the type, to see which events are listened for. */
	listensTo(type: string) {
		return (this.#listeners.get(type) ?? []).length > 0;
	}
}

/** A live connection made of fake event sources, listed in {@link FakeEventSource.instances}. */
export function fakeLive(): LiveState {
	FakeEventSource.instances = [];
	return new LiveState((url) => new FakeEventSource(url) as unknown as EventSource);
}
