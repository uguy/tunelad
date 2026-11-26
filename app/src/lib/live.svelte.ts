/** What a live event says: its type and, when it concerns one entity, the entity's id. Never the entity's content. */
export interface LiveEvent {
	type: string;
	id?: string;
}

export type LiveHandler = (event: LiveEvent) => void;

/** The server's stream of live updates. */
const URL = '/api/live';

/** `EventSource.CLOSED`: the browser has given up on the connection and will not retry. */
const CLOSED = 2;

/** How long to wait before opening the stream again after the browser gave up on it (milliseconds). */
const RETRY_DELAY = 5000;

/**
 * The browser tab's one connection to the server's live updates: tells pages, as it happens, that something they
 * display has changed, so they refetch it. Events carry identifiers only, and can be missed (while disconnected, say),
 * which is why pages also refetch when the connection is back ({@link onReconnect}).
 */
export class LiveState {
	/** Whether the stream is open right now: not while connecting, nor after a failure. */
	connected = $state(false);

	#source: EventSource | null = null;
	/** The event types the open source is listening for: `EventSource` has no way to listen for all of them. */
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- bookkeeping: nothing renders it
	readonly #listening = new Set<string>();
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- bookkeeping: nothing renders it
	readonly #handlers = new Map<string, Set<LiveHandler>>();
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- bookkeeping: nothing renders it
	readonly #reconnected = new Set<() => void>();
	/** The connection failed since it was last open, so the next time it opens, events may have been missed. */
	#lost = false;
	#retry: ReturnType<typeof setTimeout> | undefined;
	readonly #create: (url: string) => EventSource;

	/** `create` makes the connection: the browser's `EventSource` by default. */
	constructor(create: (url: string) => EventSource = (url) => new EventSource(url)) {
		this.#create = create;
	}

	/** Opens the stream, unless it is open already: a tab holds one connection. */
	open() {
		if (this.#source) return;
		clearTimeout(this.#retry);
		const source = this.#create(URL);
		this.#source = source;
		this.#listening.clear();
		source.onopen = () => {
			this.connected = true;
			if (this.#lost) {
				this.#lost = false;
				this.#reconnected.forEach((handler) => call(handler));
			}
		};
		source.onerror = () => {
			this.connected = false;
			this.#lost = true;
			// The browser reconnects by itself, except after a refused connection or an error response (a server
			// that is restarting, a proxy that answers 502): then it is closed for good, so try again later
			if (source.readyState === CLOSED && this.#source === source) {
				source.close();
				this.#source = null;
				this.#retry = setTimeout(() => this.open(), RETRY_DELAY);
			}
		};
		for (const type of this.#handlers.keys()) this.#listen(source, type);
	}

	/** Closes the stream, and stops trying to open it again. */
	close() {
		clearTimeout(this.#retry);
		this.#source?.close();
		this.#source = null;
		this.connected = false;
		this.#lost = false;
	}

	/** Calls the handler for each event of the type, until the returned function is called. */
	on(type: string, handler: LiveHandler): () => void {
		let handlers = this.#handlers.get(type);
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- bookkeeping: nothing renders it
		if (!handlers) this.#handlers.set(type, (handlers = new Set()));
		handlers.add(handler);
		if (this.#source) this.#listen(this.#source, type);
		return () => void this.#handlers.get(type)?.delete(handler);
	}

	/** Calls the handler when the stream is open again after a failure, until the returned function is called. */
	onReconnect(handler: () => void): () => void {
		this.#reconnected.add(handler);
		return () => void this.#reconnected.delete(handler);
	}

	/**
	 * Keeps the stream open once it is known who is using it, and reopens it when that changes, so that it always
	 * matches the identity of the user. `who` answers the account id, null for an anonymous visitor, or undefined
	 * while that is not known yet. Returns the function that stops following and closes the stream.
	 */
	follow(who: () => string | null | undefined): () => void {
		return $effect.root(() => {
			$effect(() => {
				if (who() === undefined) return;
				this.open();
				return () => this.close();
			});
		});
	}

	#listen(source: EventSource, type: string) {
		if (this.#listening.has(type)) return;
		this.#listening.add(type);
		source.addEventListener(type, (message) =>
			this.#dispatch(type, (message as MessageEvent).data)
		);
	}

	#dispatch(type: string, data: unknown) {
		let body: { id?: unknown };
		try {
			body = JSON.parse(String(data));
		} catch {
			return;
		}
		const id = typeof body?.id === 'string' ? body.id : undefined;
		for (const handler of [...(this.#handlers.get(type) ?? [])]) call(() => handler({ type, id }));
	}
}

/** A handler that throws must not stop the others, nor the stream. */
function call(handler: () => void) {
	try {
		handler();
	} catch (error) {
		console.error('A live update handler failed', error);
	}
}

/** The browser tab's connection to the server's live updates. */
export const live = new LiveState();
