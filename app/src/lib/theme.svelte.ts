export type Theme = 'light' | 'dark';
/** What the user chose: a theme, or `system` to follow the operating system's. */
export type ThemePreference = 'system' | Theme;

/** Where the choice is kept. app.html's inline script reads the same key, to theme the page before it paints. */
export const THEME_KEY = 'tunelad.theme';

/** The browser's `theme-color` (address bar, task switcher) of each theme: its canvas. Same values as app.html. */
export const THEME_COLORS: Record<Theme, string> = { light: '#f2f2f2', dark: '#121215' };

/** The theme in force for that choice, given whether the system asks for a dark colour scheme. */
export function resolveTheme(preference: ThemePreference, systemDark: boolean): Theme {
	return preference === 'dark' || (preference === 'system' && systemDark) ? 'dark' : 'light';
}

function isPreference(value: string | null): value is ThemePreference {
	return value === 'system' || value === 'light' || value === 'dark';
}

/** What the theme state needs of the browser, so that tests can stand in for it. */
export interface ThemeEnvironment {
	storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | undefined;
	media: Pick<MediaQueryList, 'matches' | 'addEventListener' | 'removeEventListener'> | undefined;
	root: HTMLElement | undefined;
	themeColor: HTMLMetaElement | null;
}

function browser(): ThemeEnvironment {
	if (typeof window === 'undefined') {
		return { storage: undefined, media: undefined, root: undefined, themeColor: null };
	}
	let storage: Storage | undefined;
	try {
		storage = window.localStorage;
	} catch {
		// Storage can be blocked: the choice then lasts until the page closes
	}
	return {
		storage,
		media: window.matchMedia('(prefers-color-scheme: dark)'),
		root: document.documentElement,
		themeColor: document.querySelector('meta[name="theme-color"]')
	};
}

/**
 * The app's theme: the user's choice (light, dark, or the system's, which is the default) and the theme it comes
 * to. {@link start} puts the theme on the page and keeps it up to date; the page's inline script has already done
 * so once before first paint.
 */
export class ThemeState {
	preference = $state<ThemePreference>('system');
	#systemDark = $state(false);
	/** The theme in force. */
	resolved = $derived(resolveTheme(this.preference, this.#systemDark));

	readonly #env: ThemeEnvironment;

	constructor(env: ThemeEnvironment = browser()) {
		this.#env = env;
		this.#read();
	}

	#read() {
		const stored = this.#env.storage?.getItem(THEME_KEY) ?? null;
		this.preference = isPreference(stored) ? stored : 'system';
		this.#systemDark = this.#env.media?.matches ?? false;
	}

	/** Chooses a theme and remembers it; `system` forgets the choice and follows the operating system again. */
	choose(preference: ThemePreference) {
		this.preference = preference;
		try {
			if (preference === 'system') this.#env.storage?.removeItem(THEME_KEY);
			else this.#env.storage?.setItem(THEME_KEY, preference);
		} catch {
			// Not remembered, but applied
		}
		this.#apply();
	}

	/** Applies the theme to the page and follows the system's changes. Returns what stops that. */
	start(): () => void {
		this.#read();
		this.#apply();
		const media = this.#env.media;
		const onChange = (event: Pick<MediaQueryListEvent, 'matches'>) => {
			this.#systemDark = event.matches;
			this.#apply();
		};
		media?.addEventListener('change', onChange);
		return () => media?.removeEventListener('change', onChange);
	}

	#apply() {
		const { root, themeColor } = this.#env;
		if (!root) return;
		root.dataset.theme = this.resolved;
		root.style.colorScheme = this.resolved;
		if (themeColor) themeColor.content = THEME_COLORS[this.resolved];
	}
}

/** The browser's theme. */
export const theme = new ThemeState();
