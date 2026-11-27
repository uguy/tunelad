import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const css = readFileSync(new URL('../app.css', import.meta.url), 'utf8');

/** The `--name: #hex` declarations of the first block that starts with `selector`. */
function tokens(selector: string): Record<string, string> {
	const start = css.indexOf(`${selector} {`);
	const block = css.slice(start, css.indexOf('}', start));
	return Object.fromEntries(
		[...block.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6});/g)].map((m) => [m[1], m[2]])
	);
}

function luminance(hex: string): number {
	const channel = (i: number) => {
		const c = parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16) / 255;
		return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
	};
	return 0.2126 * channel(0) + 0.7152 * channel(1) + 0.0722 * channel(2);
}

function contrast(a: string, b: string): number {
	const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
	return (hi + 0.05) / (lo + 0.05);
}

/** [foreground, background, minimum ratio]: 4.5 for text, 3 for large text and icons. */
const pairs: [string, string, number][] = [
	['ink', 'canvas', 4.5],
	['ink', 'surface', 4.5],
	['ink-muted', 'canvas', 4.5],
	['ink-muted', 'surface', 4.5],
	['accent-ink', 'surface', 4.5],
	['accent-ink', 'canvas', 4.5],
	['danger', 'surface', 4.5],
	['danger', 'danger-soft', 4.5],
	['ink', 'accent-soft', 4.5],
	['on-header', 'header', 4.5],
	['on-accent', 'accent', 3],
	['on-accent', 'accent-strong', 4.5],
	['success', 'surface', 3],
	['wave', 'surface', 3],
	['accent', 'surface', 3]
];

describe.each([
	['light', ':root'],
	['dark', ":root[data-theme='dark']"]
])('%s theme', (_name, selector) => {
	const theme = tokens(selector);

	it('defines every token the other theme defines', () => {
		expect(Object.keys(theme).sort()).toEqual(Object.keys(tokens(':root')).sort());
	});

	it.each(pairs)('%s on %s reaches %s:1', (fg, bg, minimum) => {
		expect(contrast(theme[fg], theme[bg])).toBeGreaterThanOrEqual(minimum);
	});
});
