// A smoke test of the production web bundle (#223): what `./gradlew :web:wasmJsBrowserDistribution`
// builds and web.yml publishes. It serves the bundle from a /soundboard/ subpath, like GitHub
// Pages does, loads it in headless Chrome, and checks that:
//   1. the board renders and a tile tap speaks (the page is one canvas, so a tap on the
//      Water tile that speaks "Water" is the proof it drew the board where it should);
//   2. nothing logs an error, and nothing the page asks for is missing;
//   3. the manifest and its icons load;
//   4. the service worker registers, and with the server stopped the page reloads and
//      still works.
//
// Usage: node smoke.mjs [bundle directory]   (default: ../build/dist/wasmJs/productionExecutable)
// Chrome comes from CHROME_PATH, else the usual place for the platform.

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const bundle = path.resolve(process.argv[2] ?? path.join(here, '../build/dist/wasmJs/productionExecutable'));
const PORT = 8765;
const BASE = `http://localhost:${PORT}/soundboard/`;

// The Water tile: the first of the TTS board's second row, in a 400x800 window, below the
// sticky row of its home page.
const WATER_TILE = { x: 56, y: 235 };

const TYPES = {
    '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.map': 'application/json',
    '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml', '.png': 'image/png',
    '.ttf': 'font/ttf', '.zip': 'application/zip', '.txt': 'text/plain'
};

function chromePath() {
    if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
    const candidates = {
        win32: ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'],
        darwin: ['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'],
        linux: ['/usr/bin/google-chrome', '/usr/bin/google-chrome-stable', '/usr/bin/chromium', '/usr/bin/chromium-browser']
    }[process.platform] ?? [];
    const found = candidates.find(file => fs.existsSync(file));
    if (!found) throw new Error('No Chrome found; set CHROME_PATH');
    return found;
}

/** Serves [bundle] under /soundboard/ only, like GitHub Pages; anything else is a 404. */
function startServer() {
    const sockets = new Set();
    const server = http.createServer((request, response) => {
        const url = new URL(request.url, BASE);
        const relative = decodeURIComponent(url.pathname).replace(/^\/soundboard\//, '');
        const file = path.join(bundle, relative || 'index.html');
        if (!url.pathname.startsWith('/soundboard/') || !file.startsWith(bundle) ||
            !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
            response.writeHead(404).end();
            return;
        }
        response.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
        fs.createReadStream(file).pipe(response);
    });
    server.on('connection', socket => {
        sockets.add(socket);
        socket.on('close', () => sockets.delete(socket));
    });
    return new Promise(resolve => server.listen(PORT, () => resolve({
        stop: () => new Promise(done => {
            sockets.forEach(socket => socket.destroy());
            server.close(done);
        })
    })));
}

const problems = [];
const check = (ok, what) => {
    console.log(`${ok ? 'ok  ' : 'FAIL'}  ${what}`);
    if (!ok) problems.push(what);
};

async function waitFor(page, what, fn, timeout = 30_000) {
    try {
        await page.waitForFunction(fn, { timeout, polling: 100 });
        return true;
    } catch {
        return false;
    }
}

/**
 * Waits for the app to replace its loading message and settle, then taps the Water tile and
 * waits to hear its words ("Water, please." on the TTS board).
 */
async function tapWaterAndListen(page, when) {
    const started = await waitFor(page, 'the app', () => document.title === 'Soundboard' && !document.getElementById('loading'));
    check(started, `${when}: the app starts`);
    if (!started) return;
    // It opens on the first page and then scrolls to the home page, where Water is.
    await new Promise(resolve => setTimeout(resolve, 3_000));
    await page.evaluate(() => { window.__spoken = []; });
    await page.mouse.click(WATER_TILE.x, WATER_TILE.y);
    await waitFor(page, 'speech', () => window.__spoken.length > 0, 5_000);
    const spoken = await page.evaluate(() => window.__spoken);
    check(spoken.length === 1 && spoken[0].startsWith('Water'), `${when}: tapping the Water tile speaks it (heard: ${JSON.stringify(spoken)})`);
}

const server = await startServer();
let serverRunning = true;
const browser = await puppeteer.launch({ executablePath: chromePath(), headless: true, args: ['--no-sandbox'] });
try {
    const page = await browser.newPage();
    await page.setViewport({ width: 400, height: 800 });

    // Speech is recorded instead of spoken: headless Chrome has no voices to speak with.
    await page.evaluateOnNewDocument(() => {
        window.__spoken = [];
        if (window.speechSynthesis) {
            window.speechSynthesis.speak = utterance => window.__spoken.push(utterance.text);
        }
    });

    const errors = [];
    page.on('console', message => { if (message.type() === 'error') errors.push(`console: ${message.text()}`); });
    page.on('pageerror', error => errors.push(`page error: ${error.message}`));
    page.on('response', response => {
        if (serverRunning && response.url().startsWith(BASE) && response.status() >= 400) {
            errors.push(`${response.status()} for ${response.url()}`);
        }
    });

    // 1. Online, from the subpath.
    await page.goto(BASE, { waitUntil: 'load' });
    await tapWaterAndListen(page, 'online');

    // 3. The manifest and every icon in it.
    const manifest = await page.evaluate(async () => {
        const link = document.querySelector('link[rel=manifest]');
        const response = await fetch(link.href);
        const json = await response.json();
        const icons = await Promise.all(json.icons.map(async icon => {
            const url = new URL(icon.src, response.url).href;
            return { url, ok: (await fetch(url)).ok };
        }));
        return { ok: response.ok, name: json.name, startUrl: new URL(json.start_url, response.url).href, icons };
    }).catch(error => ({ error: error.message }));
    check(manifest.ok && manifest.name === 'Soundboard', `the manifest loads (${manifest.error ?? manifest.name})`);
    check(manifest.startUrl === BASE, `the manifest starts the app at ${BASE} (${manifest.startUrl})`);
    check(manifest.icons?.length > 0 && manifest.icons.every(icon => icon.ok), 'every icon in the manifest loads');

    // 4. The service worker, and a reload with the server stopped.
    const worker = await waitFor(page, 'the service worker', () => navigator.serviceWorker.controller !== null ||
        navigator.serviceWorker.getRegistration().then(registration => registration?.active != null));
    check(worker, 'the service worker registers');
    const scope = await page.evaluate(() => navigator.serviceWorker.getRegistration().then(registration => registration?.scope));
    check(scope === BASE, `its scope is the subpath (${scope})`);
    // It keeps the files the page loaded, and the built-in board and fonts, as they arrive.
    // Waits for all of them, not just some: going offline while one is still on its way made
    // the reload below fail now and then with "Failed to fetch" (#281).
    const cached = await waitFor(page, 'the cache', async () => {
        const keys = await caches.open('soundboard-v1').then(cache => cache.keys());
        const names = new Set(keys.map(request => request.url.split('?')[0]));
        const loaded = [location.href.split('?')[0].split('#')[0], ...performance.getEntriesByType('resource').map(entry => entry.name)]
            .map(url => url.split('?')[0])
            .filter(url => url.startsWith(location.origin) && !url.endsWith('/sw.js'));
        return loaded.every(url => names.has(url)) && [...names].some(name => name.endsWith('tts-care-board.zip'));
    });
    check(cached, 'the service worker has every file the page loaded, and the built-in board');

    serverRunning = false;
    await server.stop();
    await page.reload({ waitUntil: 'load' });
    await tapWaterAndListen(page, 'offline');

    check(errors.length === 0, `no errors${errors.length ? `:\n      ${errors.join('\n      ')}` : ''}`);
} finally {
    await browser.close();
    if (serverRunning) await server.stop();
}

if (problems.length > 0) {
    console.log(`\n${problems.length} check(s) failed.`);
    process.exit(1);
}
console.log('\nThe production bundle works from a subpath, online and offline.');
