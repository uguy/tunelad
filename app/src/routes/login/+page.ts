import { get } from '#lib/api.js';
import type { PageLoad } from './$types';

/** An identity provider the user can log in through, as `GET /api/login-providers` lists it. */
export interface LoginProvider {
	id: string;
	name: string;
}

export const load: PageLoad = async ({ fetch }) => {
	// An unreachable or failing API gives an empty list, which the page reports like an unconfigured one
	const providers: LoginProvider[] = await get(fetch, 'login-providers').catch(() => []);
	return { providers };
};
