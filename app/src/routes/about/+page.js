/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
	const infoRes = await fetch('/actuator/info');
	const infoJson = await infoRes.json();

	const healthRes = await fetch('/actuator/health');
	const healthJson = await healthRes.json();

	return {
		info: infoJson,
		health: healthJson
	};
}
