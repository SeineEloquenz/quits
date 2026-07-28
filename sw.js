// Service worker for the Quits web app.
//
// The Kotlin/Wasm bundle ships stable, non-content-hashed filenames (composeApp.js/.wasm,
// skiko.*), so `immutable` HTTP caching is unsafe. This worker instead:
//   - precaches a minimal app shell so the app boots offline after the first visit,
//   - serves same-origin assets stale-while-revalidate (instant + offline, refreshed for next load),
//   - serves navigations network-first (a redeploy shows up as soon as you're online),
//   - and never intercepts the cross-origin, end-to-end-encrypted relay API (or any non-GET).
//
// Bump CACHE_VERSION whenever this file's caching logic changes, so old caches are dropped.

const CACHE_VERSION = 'v1';
const CACHE = `quits-${CACHE_VERSION}`;

// Enough to render and boot; everything else (wasm, skiko, fonts, resources) is picked up at
// runtime by the stale-while-revalidate handler on first online load.
const SHELL = ['./', './index.html', './composeApp.js'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // Only same-origin GETs are ours. The relay API is a different origin and its POSTs carry
  // encrypted payloads — those must always hit the network untouched. Let the browser handle the
  // service-worker script's own updates, too.
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname === '/sw.js') {
    return;
  }

  // Navigations (including deep links like /join, which the server rewrites to index.html):
  // network-first, refreshing the cached shell; fall back to cache when offline.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          event.waitUntil(caches.open(CACHE).then((c) => c.put('./index.html', copy)));
          return res;
        })
        .catch(() => caches.match('./index.html').then((r) => r || caches.match('./'))),
    );
    return;
  }

  // Static assets: stale-while-revalidate.
  event.respondWith(
    caches.match(req).then((cached) => {
      const network = fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            event.waitUntil(caches.open(CACHE).then((c) => c.put(req, copy)));
          }
          return res;
        })
        .catch(() => cached);
      return cached || network;
    }),
  );
});
