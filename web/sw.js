// Service worker: tiene in cache tutta l'app, così funziona anche senza rete.
const CACHE = 'freecard-__BUILD__';
const FILES = [
  './', 'index.html', 'style.css?v=__BUILD__', 'app.js?v=__BUILD__', 'codec.js?v=__BUILD__', 'stores.js?v=__BUILD__',
  'vendor/zxing.min.js', 'vendor/bwip-js-min.js', 'manifest.webmanifest',
  'icons/icon-192.png', 'icons/icon-512.png', 'icons/apple-touch-icon.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (url.origin !== location.origin) return;
  // Pagina: prima la rete (per gli aggiornamenti), poi la cache. Il resto: cache.
  if (e.request.mode === 'navigate') {
    e.respondWith(fetch(e.request).catch(() => caches.match('index.html')));
    return;
  }
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request)));
});
