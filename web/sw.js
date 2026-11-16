// Service worker: precaches every file of the app so it works fully offline.
// Bump VERSION whenever a file changes, so phones fetch the new files and drop the old cache.
const VERSION = 'v1';
const PREFIX = 'avjet-fuel-';
const CACHE = PREFIX + VERSION;

const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './calc.js',
  './i18n.js',
  './manifest.webmanifest',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/maskable-512.png',
  './icons/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(ASSETS.map((url) => new Request(url, { cache: 'reload' }))))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith(PREFIX) && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Cache first, then the network. Pages fall back to the cached index.html when offline.
self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  event.respondWith(
    caches.open(CACHE).then(async (cache) => {
      const hit = await cache.match(req, { ignoreSearch: true });
      if (hit) return hit;
      try {
        return await fetch(req);
      } catch (err) {
        if (req.mode === 'navigate') {
          const page = await cache.match('./index.html');
          if (page) return page;
        }
        throw err;
      }
    }),
  );
});
