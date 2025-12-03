const base = '/api';

type Fetch = typeof globalThis.fetch;

interface SendParams {
	method: string;
	path: string;
	data?: unknown;
	token?: string;
}

async function send(fetch: Fetch, { method, path, data, token }: SendParams) {
	const opts: RequestInit = { method, headers: {} };

	if (data) {
		(opts.headers as Record<string, string>)['Content-Type'] = 'application/json';
		opts.body = JSON.stringify(data);
	}

	if (token) {
		(opts.headers as Record<string, string>)['Authorization'] = `Token ${token}`;
	}

	const res = await fetch(`${base}/${path}`, opts);
	if (res.ok) {
		return res.json();
	}
	return [];
}

export function get(fetch: Fetch, path: string, token?: string) {
	return send(fetch, { method: 'GET', path, token });
}

export function del(fetch: Fetch, path: string, token?: string) {
	return send(fetch, { method: 'DELETE', path, token });
}

export function post(fetch: Fetch, path: string, data: unknown, token?: string) {
	return send(fetch, { method: 'POST', path, data, token });
}

export function put(fetch: Fetch, path: string, data: unknown, token?: string) {
	return send(fetch, { method: 'PUT', path, data, token });
}
