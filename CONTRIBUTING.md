# Contributing

Thanks for helping. Bug reports, app-support requests and pull requests are welcome. Everyone taking part
follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Development

- JDK 21 and the Android SDK with platform 37 (`compileSdk = 37`, `minSdk = 36`).
- Before opening a pull request, run:

  ```bash
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  ```

  Lint runs with warnings as errors.

## Project layout

| Path | Contents |
| --- | --- |
| `app/src/main/kotlin/.../core/` | Pure Kotlin decision logic: `Classifier`, `ChipTextExtractor`, `TextShortener`, `KnownApps`, `People`, `KeywordFilter`, `UpdateThrottle`. No Android imports; unit tested. |
| `app/src/main/kotlin/.../platform/` | Android glue: the listener service, `Relay`, notification building, icons, settings, activity and sample logs. |
| `app/src/main/kotlin/.../ui/` | Jetpack Compose screens. |
| `app/src/debug/` | Debug-only `PreviewReceiver` for posting sample notifications through the real classifier. |
| `app/src/test/` | JVM unit tests for `core/`. |

## Adding support for an app

Rules must come from **real notifications**, not guesses.

1. Install a debug build, enable the app in LiveIsland, and turn on **⋮ › Notification samples › Record samples**.
2. Use the app until it shows the state you care about (a ride in progress, an order on its way, …).
3. Share the samples, **remove anything personal**, and attach them to an *App support* issue — or read them
   directly with `adb shell run-as io.github.hphbryan.liveisland.debug cat files/samples.jsonl`.
4. Add the rule:
   - Filtering which of an app's notifications count → `KnownApps.isRelevant`.
   - Better chip text → `ChipTextExtractor`, or an app-specific branch if the format is unique.
   - Listing an app up front → its category in `KnownApps.BY_CATEGORY`, and a row in [docs/apps.md](docs/apps.md).
5. Add a unit test using the sample's title and text (anonymised), and say which app version and One UI
   version it was observed on.

## Pull requests

- Keep changes focused, and explain the user-visible effect.
- New user-facing strings go in both `values/strings.xml` (English) and `values-zh-rTW/strings.xml`.
- Do not add the `INTERNET` permission or any analytics or tracking dependency.

By contributing you agree that your contributions are licensed under the Apache License 2.0.
