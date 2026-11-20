const base = '/api';

type Fetch = typeof globalThis.fetch;

/**
 * The CSRF token the server keeps in the readable `XSRF-TOKEN` cookie. A logged-in browser session must send it back
 * on state-changing requests, in the `X-XSRF-TOKEN` header or a `_csrf` form field.
 */
export function csrfToken(): string | undefined {
	const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
	return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : undefined;
}

/**
 * Sends a request to the API, with the CSRF token on state-changing requests, and returns the response as is: the
 * caller decides what an error status means.
 */
export function request(fetch: Fetch, method: string, path: string, data?: unknown) {
	const headers: Record<string, string> = {};
	const opts: RequestInit = { method, headers };
	if (data !== undefined) {
		headers['Content-Type'] = 'application/json';
		opts.body = JSON.stringify(data);
	}
	if (method !== 'GET') {
		const token = csrfToken();
		if (token) headers['X-XSRF-TOKEN'] = token;
	}
	return fetch(`${base}/${path}`, opts);
}

/** GETs the API path and returns its JSON body, or an empty array if the server answers with an error status. */
export async function get(fetch: Fetch, path: string) {
	const res = await request(fetch, 'GET', path);
	return res.ok ? res.json() : [];
}
