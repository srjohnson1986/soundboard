// Headless Chrome with a fake microphone (a generated tone) that needs no permission
// prompt, and audio allowed without a click, so WebRecorderTest can record and play.
config.set({
    // Mocha's default of 2s per test is too tight for recording 1.5s of audio and then
    // decoding it, especially on a busy CI runner.
    client: { mocha: { timeout: 30000 } },
    browsers: ['ChromeHeadlessFakeMedia'],
    customLaunchers: {
        ChromeHeadlessFakeMedia: {
            base: 'ChromeHeadless',
            flags: [
                '--use-fake-ui-for-media-stream',
                '--use-fake-device-for-media-stream',
                '--autoplay-policy=no-user-gesture-required'
            ]
        }
    }
});
