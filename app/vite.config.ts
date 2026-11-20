import devtoolsJson from 'vite-plugin-devtools-json';
import { defineConfig } from 'vitest/config';
import { playwright } from '@vitest/browser-playwright';
import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';
import tailwindcss from '@tailwindcss/vite';
import { withPwa } from '@vite-pwa/sveltekit';

export default defineConfig({
	define: {
		__DATE__: `'${new Date()}'`,
		__RELOAD_SW__: true
	},
	plugins: [
		tailwindcss(),
		// SvelteKit 3: kit config lives here (svelte.config.js is no longer supported);
		// withPwa() wraps the sveltekit() plugin.
		withPwa(
			{
				adapter: adapter({
					fallback: 'index.html'
				}),
				prerender: {
					crawl: false
				},
				preprocess: vitePreprocess()
			},
			{
				scope: '/',
				base: '/',
				strategies: 'generateSW',
				// strategies: 'injectManifest',
				// filename: 'sw.ts',
				manifest: {
					short_name: 'Tune Lad',
					name: 'Tune Lad',
					start_url: './',
					scope: './',
					display: 'standalone',
					display_override: ['window-controls-overlay'],
					theme_color: '#1f1f24',
					background_color: '#f2f2f2',
					icons: [
						{
							src: 'images/icon-72x72.png',
							sizes: '72x72',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-96x96.png',
							sizes: '96x96',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-128x128.png',
							sizes: '128x128',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-144x144.png',
							sizes: '144x144',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-152x152.png',
							sizes: '152x152',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-192x192.png',
							sizes: '192x192',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-384x384.png',
							sizes: '384x384',
							type: 'image/png',
							purpose: 'maskable any'
						},
						{
							src: 'images/icon-512x512.png',
							sizes: '512x512',
							type: 'image/png',
							purpose: 'maskable any'
						}
					]
				},
				generateSW: {
					// adapter-static overwrites build/index.html with the SPA fallback, which `kit.spa` already
					// precaches as `/`. Precaching the prerendered index.html too (as `./`) makes Workbox throw
					// add-to-cache-list-conflicting-entries and the service worker never registers.
					// node_modules and server/ are the plugin's defaults, which a custom list replaces.
					globIgnores: ['**/node_modules/**/*', 'server/**', 'prerendered/pages/index.html'],
					// Navigations the service worker must leave to the server instead of serving the SPA shell
					navigateFallbackDenylist: [/^\/login/, /^\/oauth2\//, /^\/logout/, /^\/api\//]
				},
				injectManifest: {
					globPatterns: ['client/**/*.{js,css,ico,png,svg,webp,woff,woff2}']
				},
				// if you have shared info in svelte config file put in a separate module and use it also here
				kit: { trailingSlash: 'never', adapterFallback: '/', spa: true }
			}
		),
		devtoolsJson()
	],
	test: {
		expect: { requireAssertions: true },
		projects: [
			{
				extends: './vite.config.ts',
				test: {
					name: 'client',
					browser: {
						enabled: true,
						provider: playwright(),
						instances: [{ browser: 'chromium', headless: true }]
					},
					include: ['src/**/*.svelte.{test,spec}.{js,ts}'],
					exclude: ['src/lib/server/**']
				}
			},
			{
				extends: './vite.config.ts',
				test: {
					name: 'server',
					environment: 'node',
					include: ['src/**/*.{test,spec}.{js,ts}'],
					exclude: ['src/**/*.svelte.{test,spec}.{js,ts}']
				}
			}
		]
	},
	server: {
		// Login and logout are served by the backend too; /login also covers the /login/oauth2/code callback
		proxy: {
			'/api': 'http://localhost:8080',
			'/actuator': 'http://localhost:8080',
			'/login': 'http://localhost:8080',
			'/oauth2': 'http://localhost:8080',
			'/logout': 'http://localhost:8080'
		}
	}
});
