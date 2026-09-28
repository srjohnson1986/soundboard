# iOS

The iOS app is built from the same Kotlin code as the Android app and the web version
(see [Architecture](ARCHITECTURE.md), "Modules"), with a thin Swift shell around it.
It compiles, its tests pass on the iPhone simulator, and it launches, all in CI. **It's
waiting on a contributor with an [Apple Developer Program](https://developer.apple.com/programs/)
membership** ($99 a year) to share it through TestFlight, and with a Mac to try it by
hand first. A Mac and a free Apple ID are enough for that part, on your own iPhone or the
simulator. No one on the project has either yet, so iOS isn't released. If you do, this
page is for you, and for your AI assistant (see also [AGENTS.md](../AGENTS.md)).

## Where it stands

**Checked in CI on every code change** (the **iOS** workflow, `.github/workflows/ios.yml`,
on a macOS runner; advisory, it doesn't block merging):
- The shared module and the `ios` module compile for iPhone and for the simulator.
- Every shared test passes on the iPhone simulator: the model, storage, the view model
  and the shared UI tests, plus iOS's own tests for the file store, the zip codec and
  the settings store (`shared/src/iosTest`).
- The app builds with Xcode, installs on a simulated iPhone, and is still running 25
  seconds after launch. The run keeps a screenshot (the `ios-screenshots` artifact). The
  first one showed the TTS board laid out as on Android and the web, which also proved
  the app reads its built-in board zip.

**Not checked by anyone yet**, which is the first job for a contributor with a Mac:
- [ ] Tapping a tile plays its clip, or speaks it.
- [ ] Recording a tile: the microphone prompt, the recording, and the trim of its end
  (the tap on Stop shouldn't be the last thing it plays; a very short recording is kept
  whole).
- [ ] Settings → Voice lists the device's voices, and Speed, Pitch and Preview work.
- [ ] Sounds play with the ring/silent switch on silent (they should: see
  `configureAudioSession()`).
- [ ] With the media volume all the way down, the red "can't be heard" bar shows, and it
  clears once the volume is up (checked when the app comes back to the front and on
  each tap).
- [ ] Export backup opens the Files picker to choose where the zip goes; Import backup,
  picking a sound file and picking a background picture open it to choose a file.
- [ ] A backup exported on iOS imports on Android and the web, and one from each of
  them imports on iOS, recordings included. (Android and the web use their own zip
  libraries. iOS reads and writes the zip format itself; its tests read a zip made
  outside the app, but nothing has yet imported an iOS backup elsewhere.)
- [ ] Keep screen awake, rotation, the caregiver lock's hold to unlock, and Show mode.

Open an issue for anything that doesn't work, or tick items off here in a pull request.

## Build and run it

On a Mac with Xcode and a JDK 17 or later:

```bash
./gradlew :shared:iosSimulatorArm64Test     # the shared tests, on the simulator
brew install xcodegen
cd ios/app
xcodegen generate                           # writes Soundboard.xcodeproj
open Soundboard.xcodeproj
```

In Xcode, pick an iPhone simulator and press **Run**. The project's first build phase
runs Gradle (`:ios:embedAndSignAppleFrameworkForXcode`), which builds the Kotlin side
as the `SoundboardKit` framework. The first build is slow: Gradle downloads the
Kotlin/Native toolchain, about a gigabyte. After that it only rebuilds what changed.

The Xcode project isn't kept in git: `ios/app/project.yml` is its source (an
[XcodeGen](https://github.com/yonaskolb/XcodeGen) spec), so change that and run
`xcodegen generate` again, rather than changing project settings in Xcode.

Simulators on Apple silicon only: the framework isn't built for Intel simulators
(`iosX64`). On an Intel Mac, add `iosX64()` to `shared/build.gradle.kts` and
`ios/build.gradle.kts`, and remove the `EXCLUDED_ARCHS` line in `project.yml`.

## Run it on your own iPhone (free)

A free Apple ID is enough to put the app on your own device from Xcode:
1. Xcode → **Settings → Accounts**: add your Apple ID.
2. Change `PRODUCT_BUNDLE_IDENTIFIER` in `project.yml` to something only you would use
   (Apple won't sign `com.example...`), then `xcodegen generate` again.
3. Select the **Soundboard** target → **Signing & Capabilities** → Team: your Personal
   Team. (Regenerating the project resets this; don't commit your team ID.)
4. On the iPhone, turn on **Settings → Privacy & Security → Developer Mode**, connect it,
   pick it in Xcode and press **Run**.

A free Apple ID's apps stop opening after 7 days; run it from Xcode again to renew.

## Share it through TestFlight (Apple Developer Program)

This is issue [#267](https://github.com/srjohnson1986/soundboard/issues/267), closed until
someone has the membership:
1. In App Store Connect, create the app with a bundle ID of your own.
2. Put that bundle ID and your team ID in `project.yml`
   (`PRODUCT_BUNDLE_IDENTIFIER`, and `DEVELOPMENT_TEAM` under `settings.base`).
3. **Product → Archive**, then **Distribute App → TestFlight**. Anyone you invite
   installs it with the TestFlight app.
4. To automate it, which is the rest of #267, give CI a distribution certificate, a
   provisioning profile and an App Store Connect API key as repository secrets. Then
   have `ios.yml` run `xcodebuild archive` and `-exportArchive` on release tags, and
   upload the result with `xcrun altool --upload-app` (or fastlane's `pilot`). That's
   the same way `android-release.yml` signs and publishes the APK.

The version comes from `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `project.yml`.
Keep them in step with `appVersionName` and `appVersionCode` in `gradle.properties` until
something sets them from there.

## Where the iOS code is

| What | Where |
|---|---|
| Storage: files, zip, settings, built-in boards, picked and saved files | `shared/src/iosMain/.../data/IosStorage.kt`, `IosZipCodec.kt` |
| The Files picker, microphone permission, keep-awake, orientation | `shared/src/iosMain/.../ui/Platform.ios.kt` |
| Audio playback, recording, speech, the media volume | `ios/src/iosMain/.../ios/IosAudio.kt` |
| The app's screen and how it's wired up | `ios/src/iosMain/.../ios/MainViewController.kt` |
| The Swift shell | `ios/app/Sources/SoundboardApp.swift` |
| The Xcode project, Info.plist settings, the bundled board | `ios/app/project.yml` |
| iOS-only tests | `shared/src/iosTest` |

Everything else, the whole UI and all its logic, is common code that Android and the web
run too. A fix there reaches all three.

## Things that trip you up

- **iOS code only compiles on macOS.** Windows and Linux skip the iOS targets without
  complaint (`kotlin.native.ignoreDisabledTargets`), so an iOS mistake only shows up in
  the iOS workflow, or on a Mac.
- **Test names can't use `,` `(` `)` `%` `.` `;` `:` `/` `[` `]` `<` `>`**, even in
  backticks: Kotlin/Native rejects them, though the JVM and the browser don't.
- **Objective-C category members are Kotlin extensions**, which need their own import:
  `AVAudioSession.setActive` and `outputVolume` (`platform.AVFAudio.*`),
  `AVAssetExportSession.timeRange` (`platform.AVFoundation.timeRange`), and much of
  `NSData`, `NSDate` and `NSLocale` (`platform.Foundation.*`). "Unresolved reference" on
  an Apple API usually means a missing import.
- **The shared tests run with no app around them**, so there's no `UIApplication`. Code
  that needs one checks `IosHost.inApp`, which `MainViewController()` sets.
- **Only the TTS board ships**, as on the web: no one's recorded voice is in a build
  others can download. The recorded boards are Android-only (`app/src/main/assets`).
