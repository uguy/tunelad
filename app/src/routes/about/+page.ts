import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const infoRes = await fetch('/actuator/info');
	const infoJson = await infoRes.json();

	const healthRes = await fetch('/actuator/health');
	const healthJson = await healthRes.json();

	return {
		info: infoJson,
		health: healthJson
	};
};
