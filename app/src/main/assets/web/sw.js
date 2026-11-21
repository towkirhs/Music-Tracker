// Lets the website be installed and open without internet. Always asks the network first, so updates
// arrive as soon as they are published; the saved copy is only used when offline.
const CACHE = 'music-tracker';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  // Only this website's own files; never the phone's live data or other sites.
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.includes('/api/')) return;
  e.respondWith(
    fetch(e.request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then((c) => c.put(e.request, copy));
        }
        return response;
      })
      .catch(() => caches.match(e.request).then((hit) => hit || caches.match('./'))),
  );
});
