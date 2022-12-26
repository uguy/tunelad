/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
	const res = await fetch('/actuator/info');
	const json = await res.json();
	return { info: json };
}
