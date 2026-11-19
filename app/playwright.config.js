import { defineConfig } from '@playwright/test';

const CI = !!process.env.CI;

export default defineConfig({
	webServer: {
		// The Maven build has already run `npm run build` in generate-resources and sets
		// E2E_PREBUILT; standalone runs build first so they never test stale output.
		command: process.env.E2E_PREBUILT ? 'npm run preview' : 'npm run build && npm run preview',
		port: 4173,
		reuseExistingServer: !CI
	},
	testDir: 'e2e',
	forbidOnly: CI,
	retries: CI ? 1 : 0,
	reporter: [['list'], ['html', { open: 'never' }]]
});
