// Headless Chrome on a busy CI runner can be slow: the Compose UI tests render in it, and the
// browser-storage (OPFS) tests wait on disk. Mocha's default of 2 s per test leaves little room,
// and Karma's defaults (2 s to reconnect after a missed heartbeat, no retries) fail the whole
// run on one busy moment (#230's PR hit "reconnect failed before timeout of 2000ms").
config.set({
    client: { mocha: { timeout: 30000 } },
    pingTimeout: 30000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 2,
    browserNoActivityTimeout: 120000
});
