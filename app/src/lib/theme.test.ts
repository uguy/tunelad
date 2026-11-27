import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
	THEME_COLORS,
	THEME_KEY,
	ThemeState,
	resolveTheme,
	type ThemeEnvironment
} from './theme.svelte.js';

class FakeMedia extends EventTarget {
	matches: boolean;
	constructor(matches: boolean) {
		super();
		this.matches = matches;
	}
	set(matches: boolean) {
		this.matches = matches;
		this.dispatchEvent(Object.assign(new Event('change'), { matches }));
	}
}

function environment(stored: string | null, systemDark: boolean) {
	const values = new Map<string, string>(stored ? [[THEME_KEY, stored]] : []);
	const root = { dataset: {} as Record<string, string>, style: {} as Record<string, string> };
	const themeColor = { content: '' };
	const media = new FakeMedia(systemDark);
	const env = {
		storage: {
			getItem: (k: string) => values.get(k) ?? null,
			setItem: (k: string, v: string) => void values.set(k, v),
			removeItem: (k: string) => void values.delete(k)
		},
		media,
		root,
		themeColor
	} as unknown as ThemeEnvironment;
	return { env, values, root, themeColor, media };
}

describe('resolveTheme', () => {
	it.each([
		['system', true, 'dark'],
		['system', false, 'light'],
		['dark', false, 'dark'],
		['light', true, 'light']
	] as const)('%s with system dark=%s is %s', (preference, systemDark, expected) => {
		expect(resolveTheme(preference, systemDark)).toBe(expected);
	});
});

describe('ThemeState', () => {
	it('follows the system when nothing was chosen', () => {
		const { env, root } = environment(null, true);
		const state = new ThemeState(env);
		state.start();
		expect(state.preference).toBe('system');
		expect(state.resolved).toBe('dark');
		expect(root.dataset.theme).toBe('dark');
	});

	it('uses the stored choice over the system', () => {
		const { env, root } = environment('dark', false);
		const state = new ThemeState(env);
		state.start();
		expect(state.resolved).toBe('dark');
		expect(root.dataset.theme).toBe('dark');
		expect(root.style.colorScheme).toBe('dark');
	});

	it('ignores a stored value it does not know', () => {
		expect(new ThemeState(environment('purple', false).env).preference).toBe('system');
	});

	it('remembers a choice, applies it, and sets the browser colour', () => {
		const { env, values, root, themeColor } = environment(null, false);
		const state = new ThemeState(env);
		state.start();
		state.choose('dark');
		expect(values.get(THEME_KEY)).toBe('dark');
		expect(root.dataset.theme).toBe('dark');
		expect(themeColor.content).toBe(THEME_COLORS.dark);
	});

	it('forgets the choice when back to the system', () => {
		const { env, values, root } = environment('dark', false);
		const state = new ThemeState(env);
		state.start();
		state.choose('system');
		expect(values.has(THEME_KEY)).toBe(false);
		expect(root.dataset.theme).toBe('light');
	});

	it('follows a change of the system while on system, and not after a choice', () => {
		const { env, root, media } = environment(null, false);
		const state = new ThemeState(env);
		const stop = state.start();
		media.set(true);
		expect(root.dataset.theme).toBe('dark');
		state.choose('light');
		media.set(false);
		media.set(true);
		expect(root.dataset.theme).toBe('light');
		stop();
	});
});

describe('the inline script of app.html', () => {
	const html = readFileSync(new URL('../app.html', import.meta.url), 'utf8');
	// The one inline script of the page, found by position: this reads our own file, it does not filter HTML
	const start = html.indexOf('<script>') + '<script>'.length;
	const script = html.slice(start, html.indexOf('</script>', start));

	function run(stored: string | null, systemDark: boolean) {
		const root = { dataset: {} as Record<string, string>, style: {} as Record<string, string> };
		const color = { content: '' };
		new Function('localStorage', 'matchMedia', 'document', script)(
			{ getItem: () => stored },
			() => ({ matches: systemDark }),
			{ documentElement: root, querySelector: () => color }
		);
		return { theme: root.dataset.theme, scheme: root.style.colorScheme, color: color.content };
	}

	it.each([
		[null, false],
		[null, true],
		['light', true],
		['dark', false],
		['system', true],
		['purple', false]
	])('themes the page as the store does (stored %s, system dark %s)', (stored, systemDark) => {
		const state = new ThemeState(environment(stored, systemDark).env);
		const result = run(stored, systemDark);
		expect(result.theme).toBe(state.resolved);
		expect(result.scheme).toBe(state.resolved);
		expect(result.color).toBe(THEME_COLORS[state.resolved]);
	});

	it('keeps the light theme when storage throws', () => {
		const root = { dataset: {} as Record<string, string>, style: {} as Record<string, string> };
		new Function('localStorage', 'matchMedia', 'document', script)(
			{
				getItem: () => {
					throw new Error('blocked');
				}
			},
			() => ({ matches: true }),
			{ documentElement: root, querySelector: () => null }
		);
		expect(root.dataset.theme).toBeUndefined();
	});

	it('uses the same browser colours as theme.svelte.ts', () => {
		expect(html).toContain(`'${THEME_COLORS.light}'`);
		expect(html).toContain(`'${THEME_COLORS.dark}'`);
		expect(html).toContain(`name="theme-color" content="${THEME_COLORS.light}"`);
	});
});
