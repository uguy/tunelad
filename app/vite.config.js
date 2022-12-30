import {sveltekit} from '@sveltejs/kit/vite';
import {SvelteKitPWA} from '@vite-pwa/sveltekit';

/** @type {import('vite').UserConfig} */
const config = {
    plugins: [
        sveltekit({
            ssr: false,
            trailingSlash: 'never',
            adapterFallback: 'index.html'
        }),
        SvelteKitPWA({
            srcDir: './src',
            scope: '/',
            base: '/',
            manifest: {
                start_url: './',
                scope: './',
                display: 'standalone',
                theme_color: '#f3f3f3',
                background_color: '#f3f3f3',
                icons: [
                    {
                        src: 'images/tracks-144.webp',
                        sizes: '144x144',
                        type: 'image/webp'
                    },
                    {
                        src: 'images/tracks-192.png',
                        sizes: '192x192',
                        type: 'image/png'
                    },
                    {
                        src: 'images/tracks-600.png',
                        sizes: '600x600',
                        type: 'image/png'
                    },
                    {
                        src: 'images/tracks-600.webp',
                        sizes: '600x600',
                        type: 'image/webp'
                    },
                    {
                        src: 'images/tracks-600.webp',
                        sizes: '600x600',
                        type: 'image/webp',
                        purpose: 'any maskable'
                    }
                ]
            },
            injectManifest: {
                globPatterns: ['client/**/*.{js,css,ico,png,svg,webp,woff,woff2}']
            },
            // if you have shared info in svelte config file put in a separate module and use it also here
            kit: {
                trailingSlash: 'never',
                adapterFallback: '/'
            }
        })
    ],
    test: {
        include: ['src/**/*.{test,spec}.{js,ts}']
    },
    server: {
        proxy: {
            '/api': 'http://localhost:8080',
            '/actuator': 'http://localhost:8080'
        }
    }
};

export default config;
