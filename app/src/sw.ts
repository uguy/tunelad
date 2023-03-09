import {
	cleanupOutdatedCaches,
	createHandlerBoundToURL,
	precacheAndRoute
} from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';

// see https://vite-pwa-org.netlify.app/guide/inject-manifest.html

declare let self: ServiceWorkerGlobalScope;

// self.__WB_MANIFEST is default injection point
precacheAndRoute(self.__WB_MANIFEST);

// clean old assets
cleanupOutdatedCaches();

let allowlist = [/^\/$/];
// to allow work offline
registerRoute(new NavigationRoute(createHandlerBoundToURL('/'), { allowlist }));

if (Notification in window && Notification.permission != 'granted') {
	console.log('Demander authorisation');
	Notification.requestPermission((status) => {
		console.log('Status:' + status);
		displayNotification(' Notification enabled');
	});
}
const displayNotification = (notificationTitle) => {
	console.log('display notification');
	if (Notification.permission == 'granted') {
		navigator.serviceWorker.getRegistration().then((reg) => {
			console.log(reg);
			const options = {
				body: 'New track added',
				icon: '/images/icon-512x512.png',
				vibrate: [100, 50, 100],
				data: { dateOfArrival: Date.now(), primaryKey: 0 }
			};
			reg.showNotification(notificationTitle, options);
		});
	}
};
