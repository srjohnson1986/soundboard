# Working on Soundboard (for AI assistants, and people)

Soundboard is a communication board (AAC): someone who can't easily speak taps tiles
that play recorded clips or speak their words. It was built for a family member, and
people who can't easily speak rely on boards like it, so **its data must survive every
update** and nothing may make it go quiet without saying so. It's one Kotlin codebase for **Android, the web and iOS**:
[README](README.md) for what it does, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
before changing code, [docs/IOS.md](docs/IOS.md) for iOS, and
[docs/RELEASING.md](docs/RELEASING.md) for releases.

## Where code goes

| Module | What | Builds on |
|---|---|---|
| `shared/src/commonMain` | Everything a user sees or decides: model, storage logic, `BoardViewModel`, the whole Compose UI | all three |
| `shared/src/{androidMain,wasmJsMain,iosMain}` | Each platform's storage (`Storage.kt`'s interfaces) and UI hooks (`ui/Platform.kt`'s `expect`s) | its platform |
| `app/` | Android: `MainActivity`, audio and speech | Android |
| `web/` | The web app: `main()`, browser audio and speech | Web (Wasm) |
| `ios/` (+ `ios/app/`, the Swift shell) | iOS: `MainViewController()`, audio and speech | iOS, macOS hosts only |

## Keeping the three platforms in step

- **Put it in `commonMain`** whenever it can go there. A feature there reaches Android,
  the web and iOS in the same change, and its tests run on all three.
- **A new platform capability** is an interface (or `expect`) in `shared` plus an
  implementation for **each** platform in the same pull request, as with `Speaker`,
  `MediaVolume` and `FileStore`. Where a platform can't do it (a browser can't read the
  system volume), give it an honest stand-in, such as `UnknownMediaVolume`, and say so
  in a comment. Don't leave a platform out.
- **Test in `shared/src/commonTest`**: the model, the view model, storage, and the UI
  through `runUiTest` / `BoardUiTest`. `:shared:allTests` runs them on the JVM, in headless
  Chrome and, on a Mac or in the iOS workflow, on the iPhone simulator.
- **Settings that belong to the device** (voice, Show mode, the caregiver lock...) go in
  `DevicePreferences`. Settings that belong to the board go on `Board`, so they travel
  in backups.
- iOS is waiting on a contributor with an Apple Developer membership (and a Mac), so
  nothing on iOS has been tried by hand yet. Implement it anyway: CI compiles and tests
  it. Add anything that needs checking by hand to the list in docs/IOS.md.

## Workflow

- Every change: a GitHub issue, then a branch, then a pull request to `master` (protected).
  Fill in the pull request template's platform checklist.
- **Dependency bumps aren't hand-made.** Dependabot opens them weekly and they merge
  themselves when `build-and-test` passes (docs/ARCHITECTURE.md, "Testing"). Don't bump a
  version in a feature pull request unless the feature needs it; if a Dependabot pull
  request is red, fix it on its branch.
- The **CI** workflow's `build-and-test` check is required: lint, unit and shared tests
  on the JVM and in Chrome, a coverage floor, and the builds. **UI tests** (Android
  emulator) and **iOS** (macOS runner) are advisory. For a pull request that's about
  iOS, wait for `ios-tests` too.
- Update `docs/` in the same pull request: `USER_GUIDE.md` for anything a user sees,
  `ARCHITECTURE.md` for structure and tests, and `IOS.md` for iOS. CI's `docs` check fails
  on a broken link, anchor or path, and the **Docs reminder** comments on a pull request
  that changes user-facing code without the user guide or README. Answer it with the docs,
  or with the **no docs needed** label. The wiki follows `docs/` and the README
  automatically.
- Test on an emulator, never on the family's phone. Before a release, install it over
  the previous release rather than on a cleared install (RELEASING.md, step 2).

## Commands

```bash
./gradlew assembleDebug                       # Android debug APK
./gradlew testDebugUnitTest :shared:allTests  # JVM + browser tests (+ iOS simulator on a Mac)
./gradlew connectedDebugAndroidTest           # emulator UI tests
./gradlew :web:wasmJsBrowserDevelopmentRun    # the web app at http://localhost:8080
./gradlew :koverHtmlReportUnit :verifyCoreCoverage
python3 scripts/check-docs.py                # every link, anchor and path the docs name exists
./gradlew :shared:recordRoborazziAndroidHostTest   # re-record screenshots after a deliberate UI change
```

iOS (on a Mac): see docs/IOS.md, "Build and run it".

## Pitfalls that have cost time

- **Test names**: no `,` `(` `)` `%` `.` `;` `:` `/` `[` `]` `<` `>`, even in backticks
  (Kotlin/Native, which builds the iOS tests, rejects them).
- **Only macOS builds iOS.** On Windows or Linux the iOS targets are skipped silently, so
  iOS breakage only shows in the iOS workflow.
- **Compose's test clock steps one frame at a time**, so waiting minutes in a UI test takes
  minutes. Make long delays injectable (as with `LocalRelockAfterIdle`) and shorten them in
  the test.
- **With `mainClock.autoAdvance` on**, `waitForIdle()` runs animations to their end. Stop the
  clock before testing something mid-animation, such as the hold to unlock.
- **Robolectric** hangs on a text field in a landscape, platform-width dialog. The JVM
  tests turn platform width off in landscape (`LocalDialogUsesPlatformWidth`).
- **The browser tests sometimes lose headless Chrome** ("Disconnected ... no message in
  120000 ms"). A test's wait for idle is spinning on the page's only thread (#279).
  `runUiTest` now turns animation time off there and gives each test 20 seconds, so a spin
  fails one test with a timeout; that test is the one to look at. Don't time something
  with an animation (`Animatable`, `tween`) if it must not follow the system's animation
  scale, and don't rely on an animation's length in a Skia-drawn test.
- A menu's or dialog's open state must live above anything that recomposes differently
  by orientation, or rotating closes it (#237).
