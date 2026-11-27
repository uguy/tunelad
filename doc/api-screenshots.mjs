// Retakes doc/scalar.png and doc/api-docs.png from the running application (http://localhost:8080).
//
//   node doc/api-screenshots.mjs
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '../app/node_modules/@playwright/test/index.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE ?? 'http://localhost:8080';

const browser = await chromium.launch();
try {
	const context = await browser.newContext({
		viewport: { width: 1600, height: 1000 },
		colorScheme: 'light',
		reducedMotion: 'reduce'
	});
	const page = await context.newPage();

	await page.goto(`${BASE}/scalar`);
	await page.getByText('Playlists').first().waitFor();
	await page.getByRole('link', { name: /Playlists/ }).first().click().catch(() => {});
	await page.waitForTimeout(1500);
	await page.screenshot({ path: join(here, 'scalar.png') });
	console.log('doc/scalar.png');

	// The OpenAPI document: its paths, as the browser would show the JSON
	const spec = await (await context.request.get(`${BASE}/v3/api-docs`)).json();
	const paths = Object.fromEntries(
		Object.entries(spec.paths)
			.filter(([path]) => /favourite|playlists|\/live/.test(path))
			.sort(([a], [b]) => a.localeCompare(b))
			.map(([path, ops]) => [
				path,
				Object.fromEntries(Object.entries(ops).map(([m, op]) => [m, { summary: op.summary, tags: op.tags }]))
			])
	);
	const view = await context.newPage();
	await view.setViewportSize({ width: 720, height: 1100 });
	await view.setContent(
		`<pre style="font:13px/1.45 ui-monospace,monospace;margin:16px;white-space:pre-wrap">${JSON.stringify({ openapi: spec.openapi, info: spec.info, paths }, null, 2).replace(/</g, '&lt;')}</pre>`
	);
	await view.screenshot({ path: join(here, 'api-docs.png') });
	console.log('doc/api-docs.png');
} finally {
	await browser.close();
}
