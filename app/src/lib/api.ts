const base = '/api';

type Fetch = typeof globalThis.fetch;

interface SendParams {
	method: string;
	path: string;
	data?: unknown;
}

/**
 * The CSRF token the server keeps in the readable `XSRF-TOKEN` cookie. A logged-in browser session must send it back
 * on state-changing requests, in the `X-XSRF-TOKEN` header or a `_csrf` form field.
 */
export function csrfToken(): string | undefined {
	const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
	return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : undefined;
}

async function send(fetch: Fetch, { method, path, data }: SendParams) {
	const headers: Record<string, string> = {};
	const opts: RequestInit = { method, headers };

	if (data) {
		headers['Content-Type'] = 'application/json';
		opts.body = JSON.stringify(data);
	}

	if (method !== 'GET') {
		const token = csrfToken();
		if (token) headers['X-XSRF-TOKEN'] = token;
	}

	const res = await fetch(`${base}/${path}`, opts);
	if (res.ok) {
		return res.json();
	}
	return [];
}

/**
 * Sends a request to the API like {@link send}, with the CSRF token on state-changing requests, and returns the
 * response as is: the caller decides what an error status means.
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

export function get(fetch: Fetch, path: string) {
	return send(fetch, { method: 'GET', path });
}

export function del(fetch: Fetch, path: string) {
	return send(fetch, { method: 'DELETE', path });
}

export function post(fetch: Fetch, path: string, data: unknown) {
	return send(fetch, { method: 'POST', path, data });
}

export function put(fetch: Fetch, path: string, data: unknown) {
	return send(fetch, { method: 'PUT', path, data });
}
