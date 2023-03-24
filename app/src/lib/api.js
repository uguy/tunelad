const base = '/api';

async function send(fetch, { method, path, data, token }) {
	const opts = { method, headers: {} };

	if (data) {
		opts.headers['Content-Type'] = 'application/json';
		opts.body = JSON.stringify(data);
	}

	if (token) {
		opts.headers['Authorization'] = `Token ${token}`;
	}

	const res = await fetch(`${base}/${path}`, opts);
	if (res.ok) {
		return res.json();
	}
	return [];
}

export function get(fetch, path, token) {
	return send(fetch, { method: 'GET', path, token });
}

export function del(fetch, path, token) {
	return send(fetch, { method: 'DELETE', path, token });
}

export function post(fetch, path, data, token) {
	return send({ method: 'POST', path, data, token });
}

export function put(fetch, path, data, token) {
	return send(fetch, { method: 'PUT', path, data, token });
}
