// Mocha's default of 2s per test leaves little room for the browser-storage (OPFS) tests
// on a busy CI runner.
config.set({
    client: { mocha: { timeout: 30000 } }
});
