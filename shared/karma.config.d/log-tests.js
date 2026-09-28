// Logs each test as it finishes, with how long it took (#279). Karma's own output names
// no tests, so when headless Chrome stalls ("reconnect failed ... (ping timeout)") the
// last line here says where the run got to, and the times show which tests are slow.
// It logs through Karma's logger at warning level, the one Gradle shows: a plain
// console.log from here never reaches Gradle's output.
function TestLogReporter(baseReporterDecorator, logger) {
    baseReporterDecorator(this);
    const log = logger.create('test');
    this.onBrowserLog = function () {};
    this.onSpecComplete = function (browser, result) {
        const name = result.suite.concat(result.description).join(' > ');
        log.warn(String(result.time).padStart(6) + ' ms ' + (result.success ? 'ok  ' : 'FAIL') + ' ' + name);
    };
}
TestLogReporter.$inject = ['baseReporterDecorator', 'logger'];

config.plugins = (config.plugins || ['karma-*']).concat([{ 'reporter:test-log': ['type', TestLogReporter] }]);
config.reporters = (config.reporters || []).concat(['test-log']);
