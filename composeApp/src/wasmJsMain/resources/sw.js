const SCOPE = self.registration.scope;
const CACHE = 'nomad-budget-v2@' + SCOPE;
const LEGACY = new URL(SCOPE).pathname === '/nomad-budget/' ? ['nomad-budget-v1'] : [];
const IMMUTABLE = /\/[0-9a-f]{16,}\.wasm$|\/icon-\d+\.png$/;

self.addEventListener('install', function () {
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        adoptLegacy()
            .then(function () { return caches.keys(); })
            .then(function (keys) {
                const stale = keys.filter(function (k) { return k !== CACHE && (k.endsWith('@' + SCOPE) || LEGACY.indexOf(k) >= 0); });
                return Promise.all(stale.map(function (k) { return caches.delete(k); }));
            })
            .then(function () { return self.clients.claim(); })
    );
});

self.addEventListener('fetch', function (event) {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin || !url.href.startsWith(SCOPE)) return;

    if (IMMUTABLE.test(url.pathname)) {
        event.respondWith(cacheFirst(request));
    } else {
        event.respondWith(networkFirst(request));
    }
});

async function adoptLegacy() {
    const target = await caches.open(CACHE);
    for (const name of LEGACY) {
        if (!(await caches.has(name))) continue;
        const legacy = await caches.open(name);
        for (const request of await legacy.keys()) {
            if (await target.match(request)) continue;
            const response = await legacy.match(request);
            if (response) await target.put(request, response);
        }
    }
}

async function cacheFirst(request) {
    const cache = await caches.open(CACHE);
    const hit = await cache.match(request);
    if (hit) return hit;
    const response = await fetch(request);
    if (response.ok) cache.put(request, response.clone());
    return response;
}

async function networkFirst(request) {
    const cache = await caches.open(CACHE);
    try {
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
    } catch (error) {
        const hit = await cache.match(request);
        if (hit) return hit;
        if (request.mode === 'navigate') {
            const index = await cache.match('./index.html') || await cache.match('./');
            if (index) return index;
        }
        throw error;
    }
}
