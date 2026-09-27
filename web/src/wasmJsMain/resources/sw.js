// Lets the web version open without a connection once it has been visited online.
//
// Network first: while online, every request goes to the server as usual (through the
// browser's normal HTTP cache), so a new release shows up on the next visit, and each
// response is also kept here. Offline, the kept copy answers instead. The board itself
// isn't in here at all: it lives in the browser's own storage (OPFS), not in requests.
const CACHE = 'soundboard-v1';

// Files a first visit may not request but offline use can need: the bundled label fonts
// (loaded only when chosen), the built-in board, and the icons.
const EXTRAS = [
    'manifest.webmanifest',
    'boards/tts-care-board.zip',
    'composeResources/com.example.soundboard.resources/font/atkinson_hyperlegible_regular.ttf',
    'composeResources/com.example.soundboard.resources/font/atkinson_hyperlegible_bold.ttf',
    'composeResources/com.example.soundboard.resources/font/lexend.ttf',
    'icons/icon.svg',
    'icons/icon-192.png',
    'icons/icon-512.png',
    'icons/apple-touch-icon.png'
];

// Caches each URL on its own, so one that fails doesn't stop the rest.
function keep(urls) {
    return caches.open(CACHE).then(cache => Promise.allSettled(urls.map(url => cache.add(url))));
}

self.addEventListener('install', event => {
    self.skipWaiting();
    event.waitUntil(keep(EXTRAS));
});

// The page sends the files it loaded before this worker was running (index.html sends them
// once it's ready), so the first visit is enough to work offline.
self.addEventListener('message', event => {
    const urls = (event.data && event.data.keep) || [];
    event.waitUntil(keep(urls.filter(url => new URL(url).origin === self.location.origin)));
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(names => Promise.all(names.filter(name => name !== CACHE).map(name => caches.delete(name))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
    event.respondWith(
        fetch(request)
            .then(response => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE).then(cache => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => caches.match(request, { ignoreSearch: true }).then(cached => cached || Response.error()))
    );
});
