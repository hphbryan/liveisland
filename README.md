# LiveIsland

[![Download](https://img.shields.io/github/v/release/hphbryan/liveisland?label=Download&logo=android)](https://github.com/hphbryan/liveisland/releases/latest) [![CI](https://github.com/hphbryan/liveisland/actions/workflows/ci.yml/badge.svg)](https://github.com/hphbryan/liveisland/actions/workflows/ci.yml) [![License](https://img.shields.io/github/license/hphbryan/liveisland)](LICENSE)

**Requires Android 16 or later.** [繁體中文](README.zh-TW.md)

LiveIsland turns other apps' ongoing notifications — deliveries, rides, navigation, uploads,
timers — into **Android 16 Live Updates**, so they appear in the status bar chip and in
**Samsung's Now Bar** even when the app itself doesn't support them. It can also flash a short
"Replied" Live Update when an AI assistant (ChatGPT, Claude, Gemini, …) finishes answering.

- **No internet permission**, no analytics, no ads. Notification content never leaves the device.
- **Event driven**: no polling, no wake locks, no foreground service. It only runs when a notification arrives.
- **Uses the system UI**: the result is a real Live Update, not an overlay drawn over other apps.

## Which apps?

Any app you choose. Apps known or expected to show something in progress — taxis, delivery, bike
share, transit, workouts, focus timers, uploads — are suggested when installed; see
[docs/apps.md](docs/apps.md) for the list and how well each one is confirmed. For each app you can
choose whether one-off notifications (such as AI replies) appear briefly and for how long, which of its
kinds of notifications to show (kinds named like promotions start off), and words that make a notification
be skipped, for that app or for all apps at once. **Add apps** can turn on every suggested app in one tap,
the home screen suggests apps seen with something in progress, and a Quick Settings tile pauses everything.

For chat apps (LINE, WhatsApp, Messenger, Telegram, Discord and others) you can pick **people**: their
messages always appear, and can stay in the Now Bar until you read them. Everyone else's messages appear
only if you set **Everyone else** to **Show briefly**.

## Requirements

- Android 16 or later (One UI 8 or later on Samsung). Tested on One UI 9 / Android 17.
- On Samsung, Live Updates from apps that are not on Samsung's allowlist only appear when
  **Settings › Developer options › Live notifications for all apps** is on.

## Install

1. Download the latest `LiveIsland-x.y.z.apk` from [Releases](../../releases/latest) on your phone and open it.
   Allow your browser or Files app to install apps when asked.
2. Open LiveIsland and follow the steps at the top: allow notification access, allow notifications, and allow
   Live Updates. If the notification access switch is greyed out, open **Settings › Apps › LiveIsland**, tap
   **⋮ › Allow restricted settings**, and try again; Android asks this of every app installed outside a store.
3. **Samsung only:** turn on Developer options (Settings › About phone › Software information, tap
   **Build number** seven times), then turn on **Settings › Developer options › Live notifications for all
   apps**. Without it, One UI only shows Live Updates from apps on Samsung's allowlist.
4. Tap **Add apps** and turn on the apps you use, then **Send a test** from the home screen.

To update, install the newer APK over the old one; your settings are kept. To be told about new versions,
choose **Watch › Custom › Releases** at the top of this page on GitHub.

## How it works

1. `RelayListenerService` (a `NotificationListenerService`) receives notifications from the apps you enabled.
2. `Classifier` decides what to do with each one:
   - **Ongoing activity** — ongoing, or with a progress bar or chronometer → relayed for as long as it lasts.
   - **One-off** (e.g. an AI reply) → relayed for a few seconds (5 to 30, 8 by default) if
     the app is set to show one-off notifications.
   - Skipped: media, calls, group summaries, system notifications, and anything the app already posts as a
     Live Update (even if you switched Live Updates off for it) or a Samsung Live Notification, anything you
     minimised or paused in system settings, promotions and recommendations (as the app or Android marks them),
     "running in the background" notices, and one-offs while Do Not Disturb would silence them.
3. The status bar chip says what is happening before you expand it. `ChipTextExtractor` finds a value
   (remaining minutes, "arriving", stops to go, distance, a clock time or a percentage) and the chip adds
   what it is about: "3 min 12 to Central Station". Messages show "Amy: on my way", AI replies "Replied" beside the app's icon,
   other one-offs the notification's own words. `TextShortener` fits it to about 16 columns (CJK characters
   count as two); One UI scrolls what does not fit in the chip.
4. `LiveNotifications` posts a notification that meets the Live Update criteria (ongoing, titled,
   `ProgressStyle`/`BigTextStyle`, requests promotion). It also sets One UI's own extras so the Now Bar
   shows the **source app's icon and name** instead of LiveIsland's.
5. On the lock screen the copy never shows more than the source would: if the source app hides its content
   there, the copy is hidden too.
6. Tapping it opens the source app. Up to two of the source's buttons (such as an inline reply) are carried
   over and still go to that app, and a Dismiss button hides the relayed copy until the source ends.
   When the source notification goes away, so does the relayed copy.

The decision logic in `core/` is plain Kotlin with no Android dependencies and is covered by unit tests.

## Build and install

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs next to a release build as `io.github.hphbryan.liveisland.debug`. To make a signed
release, see [RELEASING.md](RELEASING.md).

## Limitations

- The source app's own notification stays in the shade; Android does not let other apps hide ongoing notifications.
- Notifications drawn with custom layouts (for example Google Clock's timer) have no readable text.
- Chip text is inferred from free text, so some apps need app-specific rules — see [CONTRIBUTING.md](CONTRIBUTING.md).
- Samsung's expanded card header always names the posting app (LiveIsland); only privileged apps can change it.
- Tapping the copy uses the source's own tap action; a few apps allow it only once, so the original may then do nothing.
- Tapping a relayed message does not mark it read in the source app's notification.
- One UI's extras used for the source app's icon and name are undocumented and may change between versions.

## Privacy

See [PRIVACY.md](PRIVACY.md). In short: notifications are read on the device to decide what to show,
nothing is collected or transmitted, and the optional sample log stays on the device unless you share it.

## License

[Apache License 2.0](LICENSE). Not affiliated with Samsung, Google or any of the apps it relays.
