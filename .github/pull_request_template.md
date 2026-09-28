Closes #

## What changed


## Platforms

Soundboard is one codebase for Android, the web and iOS (AGENTS.md, "Keeping the three platforms in step").

- [ ] Only shared code changed, so all three platforms get it, or:
- [ ] Android
- [ ] Web
- [ ] iOS: builds and passes its tests in the iOS workflow. Anything that needs checking by hand is added to docs/IOS.md.

## Checks

- [ ] Tests in `shared/src/commonTest` where they can go (they run on the JVM, in Chrome and on the iOS simulator)
- [ ] `docs/` updated: `USER_GUIDE.md` for anything a user sees, `ARCHITECTURE.md` for structure and tests, `IOS.md` for iOS. If a user sees no change, add the **no docs needed** label, which the Docs reminder looks for.
