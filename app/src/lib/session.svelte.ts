/** The logged-in user's account, as `GET /api/me` returns it. */
export interface Account {
	id: string;
	displayName: string;
	/** Whether the user has the identity provider's admin role: may edit and delete any track. */
	admin: boolean;
}

class Session {
	/** Who is logged in: null for an anonymous visitor (or until {@link load} has answered). */
	account = $state.raw<Account | null>(null);

	/** Asks the server who is logged in; `GET /api/me` answers 401 to anonymous visitors. */
	async load(fetchFunc: typeof fetch = fetch) {
		try {
			const res = await fetchFunc('/api/me');
			this.account = res.ok ? await res.json() : null;
		} catch {
			this.account = null;
		}
	}
}

/** The browser's session with the server. */
export const session = new Session();
