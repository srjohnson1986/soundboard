# Contributing to Soundboard

Thanks for helping. Soundboard is a communication aid: someone who can't easily speak
taps tiles that play recorded clips or speak their words. The Android app is in daily
use, so two things matter more than anything else:

- **Data must survive every update.** A change to how boards are stored needs a way to
  read what the previous release wrote.
- **Nothing may go quiet without saying so.** If a sound can't play, the board says why.

## Before you start

1. **Open an issue** (or comment on one) describing the bug or the change, so we can agree
   on it before you spend time on it.
2. **Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**, and [AGENTS.md](AGENTS.md) for where
   code goes and how the Android, web and iOS versions stay in step. It's one Kotlin
   codebase; put code in `shared/src/commonMain` whenever it can go there.

## Making a change

1. Fork the repository, and branch from `master`.
2. Make the change, with tests in `shared/src/commonTest` where they can go.
3. Update `docs/` in the same change: `USER_GUIDE.md` for anything a user sees,
   `ARCHITECTURE.md` for structure and tests, `IOS.md` for iOS.
4. Open a pull request to `master` and fill in the template's platform checklist.
   Use `Closes #<issue>` so the issue links up.

The commands are in [AGENTS.md](AGENTS.md#commands) and the [README](README.md#tests). The
CI `build-and-test` check (lint, the tests on the JVM and in Chrome, a coverage floor and
the builds) has to pass before a pull request can merge. The first time you contribute, a
maintainer has to approve the workflow run.

Please **test on an emulator or a device of your own**. Don't try changes on someone else's
copy of the app.

## Dependencies

Dependabot opens version bumps weekly and they merge themselves when CI passes, so don't
bump versions in a feature pull request unless the feature needs it. If you add a library,
font, image or sound, add it to [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Forks that publish their own build

Change the Android `applicationId` (`app/build.gradle.kts`) and the iOS bundle ID
(`ios/app/project.yml`) from `com.example.soundboard`, and sign with your own key.
`com.example.soundboard` is the identity of the existing app, whose updates install over
one another, so please don't ship a different build under it.

## License

By contributing you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), like the rest of the project.

## Conduct and security

Be kind: see the [Code of Conduct](CODE_OF_CONDUCT.md). To report a security problem, see
[SECURITY.md](SECURITY.md); don't open a public issue for it.
