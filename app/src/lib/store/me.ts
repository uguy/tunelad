import { writable } from 'svelte/store';

/** The logged-in user's account, as `GET /api/me` returns it. */
export interface Account {
	id: string;
	displayName: string;
	/** Whether the user has the identity provider's admin role: may edit and delete any track. */
	admin: boolean;
}

/** Who is logged in: null for an anonymous visitor (or until {@link loadMe} has answered). */
const me = writable<Account | null>(null);

/** Asks the server who is logged in; `GET /api/me` answers 401 to anonymous visitors. */
export async function loadMe(fetchFunc: typeof fetch = fetch) {
	try {
		const res = await fetchFunc('/api/me');
		me.set(res.ok ? await res.json() : null);
	} catch {
		me.set(null);
	}
}

export default me;
