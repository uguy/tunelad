import devtoolsJson from 'vite-plugin-devtools-json';
import { defineConfig } from 'vitest/config';
import { playwright } from '@vitest/browser-playwright';
import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { SvelteKitPWA } from '@vite-pwa/sveltekit';

export default defineConfig({
	define: {
		__DATE__: `'${new Date()}'`,
		__RELOAD_SW__: true
	},
	plugins: [
		tailwindcss(),
		sveltekit(),
		SvelteKitPWA({
			srcDir: './src',
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
				theme_color: '#f3f3f3',
				background_color: '#f3f3f3',
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
			injectManifest: {
				globPatterns: ['client/**/*.{js,css,ico,png,svg,webp,woff,woff2}']
			},
			// if you have shared info in svelte config file put in a separate module and use it also here
			kit: { trailingSlash: 'never', adapterFallback: '/', spa: true }
		}),
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
		proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' }
	}
});
