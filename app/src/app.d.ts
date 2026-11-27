// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	// build date, replaced at build time by vite `define` (see vite.config.ts)
	const __DATE__: string;

	namespace App {
		// interface Error {}
		// interface Locals {}
		// interface PageData {}
		interface PageState {
			/** The track just uploaded, shown on the tracks page before search finds it. */
			uploaded?: import('./routes/tracks/Tracks').Track;
			/** Set on a Set's page right after creating it asked for a list that had a Set already. */
			existed?: boolean;
		}
		// interface Platform {}
	}
}

export {};
