# Third-party notices

Soundboard is licensed under the [Apache License 2.0](LICENSE). It includes or builds on
the work below, each under its own license.

## Fonts (bundled in the app and the web version)

| Font | Copyright | License |
|---|---|---|
| Atkinson Hyperlegible | 2020 Braille Institute of America, Inc. | SIL Open Font License 1.1 |
| Lexend | 2018 The Lexend Project Authors | SIL Open Font License 1.1 |

The license texts are in `app/src/main/assets/licenses/`, and the web version serves
the same files from its `licenses/` folder (`web/src/wasmJsMain/resources/licenses/`).
The font files themselves are in `shared/src/commonMain/composeResources/font/`.

## Libraries that ship in the app

| Library | License |
|---|---|
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| Compose Multiplatform (runtime, foundation, UI, Material 3, resources, Material icons) | Apache-2.0 |
| AndroidX (Activity, Core, Lifecycle, Compose) | Apache-2.0 |
| [fflate](https://github.com/101arrowz/fflate) (the web version's zip reader and writer) | MIT |

## Tools that don't ship

Used to build and test, and not part of what's distributed: the Gradle wrapper
(Apache-2.0), JUnit 4 (EPL-1.0), Robolectric (MIT), Roborazzi (Apache-2.0), Kover
(Apache-2.0) and the Kotlin, Android and Compose Gradle plugins (Apache-2.0).

## Keeping this current

Dependabot bumps versions, which doesn't change a license. When a pull request adds a
new library or a new bundled font, image or sound it didn't write itself, add it here
(and to [NOTICE](NOTICE) if it isn't code).
