// Headless Chrome with a fake microphone (a generated tone) that needs no permission
// prompt, and audio allowed without a click, so WebRecorderTest can record and play.
config.set({
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
