import { defineConfig } from '@playwright/test';

const CI = !!process.env.CI;

export default defineConfig({
	webServer: {
		// The Maven build has already run `bun run build` in generate-resources and sets
		// E2E_PREBUILT; standalone runs build first so they never test stale output.
		command: process.env.E2E_PREBUILT ? 'bun run preview' : 'bun run build && bun run preview',
		port: 4173,
		reuseExistingServer: !CI
	},
	testDir: 'e2e',
	use: {
		// A registered service worker would show its offline-ready toast and could serve requests that
		// tests mock with page.route; service-worker.test.js opts back in
		serviceWorkers: 'block'
	},
	forbidOnly: CI,
	retries: CI ? 1 : 0,
	reporter: [['list'], ['html', { open: 'never' }]]
});
