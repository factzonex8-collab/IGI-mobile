// Keeps the game data the player has downloaded on their device, so the next mission does not fetch shared
// textures, sounds or packs again, and keeps the app shell (the page, its scripts, the icons and the web app
// manifest) so the game installed on a home screen opens without a network round trip for each file
// (registered by main.ts in a release build only; docs/gameplay/mobile.md "Service worker" and "Offline").
//
// Data: content-addressed files (a 16-hex hash in the name: tex/, snd/, model and terrain packs, chars/, menu/, hud/,
// movies/ ...: a new build gives changed content a new name, tools/deploy_r2.py) are served cache-first and kept for
// good. Fixed names (manifest.json, offline-index.json, the *.json.gz tables, levels/levelN.json.gz, lang/) go to the
// network first and fall back to the stored copy offline (at once while the browser knows it is offline).
//
// Movies: the outro is played by a <video> with range requests. A downloaded movie (offline.ts) is served from the
// store, sliced for the Range asked (206); one that is not stored is left to the browser as before.
//
// Offline Mode (offline.ts, the mission manager) fills the same store from the page: its requests carry ?igi-offline and pass
// straight to the network (the page stores them under the plain URL, without a second copy from here).
//
// App shell (same origin, inside the worker's scope, e.g. /game/): the page itself goes to the network first (so a
// new release is picked up at once) and falls back to the stored copy offline; Vite's hashed files under assets/
// never change under their name, so they are cache-first; everything else (icons, manifest) network-first.
// offline.ts also stores every file of the build (shell-files.json), including lazily loaded ones.
const CACHE = 'igi1-data-v1';
const SHELL = 'igi1-shell-v1';

const SCOPE = new URL(self.registration.scope);

self.addEventListener('install', (e) => {
  self.skipWaiting();
  // the first visit loaded the page before this worker existed: store the page and the files it names now
  e.waitUntil(precache().catch(() => { /* filled as the files are fetched */ }));
});
self.addEventListener('activate', (e) => e.waitUntil((async () => {
  const keep = new Set([CACHE, SHELL]);
  for (const k of await caches.keys()) if (k.startsWith('igi1-shell-') && !keep.has(k)) await caches.delete(k);
  await purgeBadFiles();
  await self.clients.claim();
})()));

/** A built file answered with the site's HTML page: for a moment during a release the host answers so (status 200) for a name it
 *  does not have yet. Kept, it broke the game on that device for good: the texture workers never started and the loading bar stayed
 *  where it was (user report 2026-11-06). Never stored, and copies stored by earlier workers are thrown away. */
function isPageForFile(url, res) {
  if (!res || !/\/assets\/[^?#]+\.[a-z0-9]+([?#]|$)/i.test(url) || /\.html?([?#]|$)/i.test(url)) return false;
  return /^text\/html/i.test(res.headers.get('content-type') || '');
}
async function purgeBadFiles() {
  try {
    const cache = await caches.open(SHELL);
    for (const req of await cache.keys()) {
      if (isPageForFile(req.url, await cache.match(req))) await cache.delete(req);
    }
  } catch { /* the store cannot be read: nothing to do */ }
}

async function precache() {
  const cache = await caches.open(SHELL);
  const res = await fetch(SCOPE.href, { cache: 'no-cache' });
  if (!res.ok) return;
  const html = await res.clone().text();
  await cache.put(SCOPE.href, res);
  const urls = new Set(['manifest.webmanifest', 'icons/icon-192.png', 'icons/apple-touch-icon.png']);
  for (const m of html.matchAll(/(?:src|href)="([^"]+)"/g)) urls.add(m[1]);
  for (const m of html.matchAll(/url\(([^)"']+)\)/g)) urls.add(m[1]);        // the fonts of the inline <style>
  await Promise.all([...urls].map(async (u) => {
    const url = new URL(u, SCOPE.href);
    if (url.origin !== SCOPE.origin || !url.href.startsWith(SCOPE.href) || url.pathname.endsWith('/sw.js')) return;
    if (await cache.match(url.href)) return;
    try { const r = await fetch(url.href); if (r.ok && !isPageForFile(url.href, r)) await cache.put(url.href, r); } catch { /* fetched later */ }
  }));
}

// the data root (main.ts passes DATA_URL: '/data' locally, the R2 bucket's public URL in a release build)
const ROOT = new URL(new URL(self.location.href).searchParams.get('data') || '/data', self.location.origin).href.replace(/\/?$/, '/');

/** The path below the data root, or null for anything else (pages, scripts, other sites). */
function dataPath(url) {
  return url.startsWith(ROOT) ? url.slice(ROOT.length).split('?')[0] : null;
}

/** A content-addressed name (never changes under its name): a 16-hex hash in the file name. */
const hashed = (path) => /(^|[/.])[0-9a-f]{16}\./.test(path.slice(path.lastIndexOf('/') + 1));

/** The browser says it has no network: skip the attempt (a phone with no signal can take long to fail). */
const offline = () => self.navigator && self.navigator.onLine === false;

/** The store, or null when the device cannot give one (storage full or damaged, private mode): the game then just downloads.
 *  A failure here must never fail the request: every file of the game goes through this worker. */
async function openStore(name) {
  try { return await caches.open(name); } catch { return null; }
}

/** Network first, the stored copy when offline (`key`: what to look up instead of the request). */
async function networkFirst(cacheName, req, key) {
  const cache = await openStore(cacheName);
  const stored = async () => { try { return await cache?.match(key ?? req, { ignoreSearch: true, ignoreVary: true }); } catch { return undefined; } };
  if (offline()) {
    const hit = await stored();
    if (hit) return hit;
  }
  try {
    const res = await fetch(req);
    if (res.ok && cache) cache.put(key ?? req, res.clone()).catch(() => { /* storage full: still served */ });
    return res;
  } catch (err) {
    const hit = await stored();
    if (hit) return hit;
    throw err;
  }
}

async function cacheFirst(cacheName, req) {
  const cache = await openStore(cacheName);
  let hit;
  try { hit = await cache?.match(req, { ignoreVary: true }); } catch { /* a damaged store: from the network */ }
  if (hit && !isPageForFile(req.url, hit)) return hit;
  if (hit) cache.delete(req).catch(() => { /* next time */ });
  let res = await fetch(req);
  // (the browser's own cache can hold the same wrong answer: past it, once)
  if (isPageForFile(req.url, res)) { try { res = await fetch(req.url, { cache: 'reload' }); } catch { /* the first answer stands */ } }
  if (res.ok && cache && !isPageForFile(req.url, res)) cache.put(req, res.clone()).catch(() => { /* storage full: still served */ });
  return res;
}

/** A stored movie for a <video>: the whole file, or the part a Range header asks for (206). */
async function rangeResponse(req, stored) {
  const buf = await stored.arrayBuffer();
  const type = stored.headers.get('Content-Type') || 'video/webm';
  const size = buf.byteLength;
  const m = /^bytes=(\d*)-(\d*)$/.exec((req.headers.get('Range') || '').trim());
  if (!m || (!m[1] && !m[2])) {
    return new Response(buf, { status: 200, headers: { 'Content-Type': type, 'Content-Length': String(size), 'Accept-Ranges': 'bytes' } });
  }
  let start, end;
  if (m[1]) { start = Number(m[1]); end = m[2] ? Math.min(Number(m[2]), size - 1) : size - 1; } else {
    start = Math.max(0, size - Number(m[2])); end = size - 1;                  // bytes=-N: the last N
  }
  if (start >= size || end < start) {
    return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${size}` } });
  }
  return new Response(buf.slice(start, end + 1), {
    status: 206,
    headers: {
      'Content-Type': type, 'Content-Length': String(end - start + 1), 'Content-Range': `bytes ${start}-${end}/${size}`,
      'Accept-Ranges': 'bytes',
    },
  });
}

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const path = dataPath(req.url);
  if (path === null) {
    // the app shell: same origin, inside the scope (data on the same origin was handled above)
    const url = new URL(req.url);
    if (url.origin !== SCOPE.origin || !url.href.startsWith(SCOPE.href) || url.pathname.endsWith('/sw.js')) return;
    if (url.searchParams.has('igi-offline')) return;                          // offline.ts storing the shell
    if (req.mode === 'navigate') {
      // ?level=N links and the bare page are the same document: stored once under the scope URL
      e.respondWith(networkFirst(SHELL, req, SCOPE.href));
      return;
    }
    const rel = url.href.slice(SCOPE.href.length).split('?')[0];
    e.respondWith(rel.startsWith('assets/') ? cacheFirst(SHELL, req) : networkFirst(SHELL, req));
    return;
  }
  if (path === '' || req.url.includes('igi-offline')) return;               // offline.ts downloading: its own store
  if (path.startsWith('movies/') && path.endsWith('.webm')) {
    // the outro movie: from the store when it was downloaded, else streamed by the browser as before
    e.respondWith((async () => {
      const store = await openStore(CACHE);
      let stored;
      try { stored = await store?.match(req.url.split('?')[0], { ignoreVary: true }); } catch { /* from the network */ }
      return stored ? rangeResponse(req, stored) : fetch(req);
    })());
    return;
  }
  e.respondWith(hashed(path) ? cacheFirst(CACHE, req) : networkFirst(CACHE, req));
});
