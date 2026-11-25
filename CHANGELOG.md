# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.1] - 2026-11-26

### Fixed
- In the release APK, switches, buttons and choices often seemed to do nothing: the change was saved, but the
  screen did not show it until the app was reopened. The code shrinker removed what kept the screens informed
  of changes. Debug builds were not affected, which is why it was missed. This was also the case in 0.1.0 and 0.2.0.
- Tapping Undo after clearing Recent activity could close the app while an app was being relayed.
- On Android 17, the overview card could stay on screen with old values after the app was restarted, and came
  back after being swiped away. It now goes away with its activities and stays away once swiped, until the
  activities change.
- A suggestion for an app that had since been uninstalled could keep every other suggestion from appearing.
- "Number" in the status bar picked ordinary words as units ("12 to" in "Bus 12 to Central") and pieces of
  longer numbers ("234 km" of "1,234 km").
- On the page of a messaging app, choosing people comes first again, before how the status bar looks.
- Pasted settings are read whatever their case ("On", "Number").
- Opening LiveIsland from its notifications or the Quick Settings tile brings back the screen already open
  instead of opening a second one.
- Copy all settings counts only apps that are installed.

## [0.2.0] - 2026-11-26

### Added
- **Choose what the chip shows**, per app: automatic, the title, the text, or a number with its unit
  ("3 stops", "12 km"). When a notification has nothing for that choice, the automatic chip is shown and
  Recent activity says so. Relayed activities have an **Adjust** button that opens the app's page, when the
  source's own buttons leave room for it.
- **Keep the chip for an app**: the status bar has room for one chip, and it goes to whatever was added last.
  An app set to keep it gets it back when another activity starts; one-offs and messages still take it while
  they last.
- **Overview card** on Android 17: while two or more activities are going on, one more card lists each with its
  value (minutes, percent, a running timer). It can be turned off in the menu.
- **Suggestions from what you dismiss**: after dismissing an app's copies 5 times, or one kind of its
  notifications 3 times, the home screen offers to stop showing that app or kind. Only counts are kept, on
  this phone.
- **Copy and paste settings** as text, for one app or all of them, to move them to another phone or share them.
  People are never included.
- About shows the developer, source code, release notes, privacy and license, and has **Report a problem**:
  it copies the app and phone versions and opens GitHub issues.
- Undo after removing a skipped word, a person, or clearing recent activity.
- A banner on the home screen while notification samples are being recorded.
- A setup step for when LiveIsland's own notification channel is turned off.

### Changed
- The progress bar shows what is moving: a car for rides, a bus for transit, a bag for deliveries and an
  arrow for uploads, in the app's colour. Other apps get a plain bar; the app's icon is already on the card,
  so it no longer rides the bar too. The bar and its marker stay the same colour in light and dark mode.
- On Pixel and other phones without One UI, the status bar chip now shows its text. Those phones drop
  chip text that does not fit whole and show only the icon, which happened to most relayed copies; there
  the chip now shows just the value ("12 min", "45%") or the start of the message, measured to fit.
- Pausing or resuming from the menu now says so, with Undo, and Paused shows even before setup is finished.
- The Quick Settings tile opens LiveIsland when setup is not finished instead of doing nothing.
- Setup buttons open the right settings page on more phones, and say so when a page cannot be opened. If
  notifications were blocked for good, the button opens the notification settings instead of asking again.
- The test notification lasts a minute and opens LiveIsland when tapped.
- Settings are included in Android's backup and device transfer. Turning an app off keeps its settings and
  forgets only its recent senders.
- Skipped words are removed with their × only, so a stray tap no longer removes one.
- Suggested apps leave out the phone's own system apps; apps no longer installed are hidden from the list.
- The battery notice says where to find **Unrestricted**.
- Dark mode no longer flashes white while the app opens.
- Adding a person or a skipped word opens the keyboard right away, and its Done key saves.
- The Samsung-only setup hint shows only on Samsung phones.
- The AI assistants sample chip matches what is really shown: "Replied" next to the app's icon.

## [0.1.0] - 2026-11-25

First public version.

### Relaying
- Ongoing notifications from the apps you choose appear as Android 16 Live Updates, in the status bar chip and
  Samsung's Now Bar, with the source app's icon and name in the Now Bar and on the Always On Display.
- One-off notifications, such as an AI assistant's reply, can appear for a few seconds (5, 8, 15 or 30, per app).
- Chat apps (LINE, WhatsApp, Messenger, Telegram, Discord, …) have **People**: messages from people you pick
  always appear, briefly or until read.
- Up to two of the source notification's buttons are carried over, including inline reply, plus a Dismiss
  button that hides the relayed copy until the source ends.
- Apps that post their own Live Updates are left alone, including when Live Updates are switched off for them.
- Notifications you minimised or paused in system settings stay hidden; during Do Not Disturb only what is in
  progress is shown. On the lock screen a copy never shows more than its source.
- Promotions and recommendations (as the app or Android's own classification marks them) and "running in the
  background" notices are skipped.
- Per-app words that skip a notification (for example promotions); words in Latin letters match at the start
  of a word, so "sale" does not skip "wholesale".
- When the lock screen hides notification content, relayed messages and one-offs show only the app's name.

### Chip text
- A value first: remaining minutes, "arriving", stops to go, distance, a clock time or a percentage, followed by
  what it is about ("3 min 12 to Central Station"). Messages show who and how they start ("Amy: on my way"),
  AI replies just "Replied" next to the app's icon, other one-offs their own words.
- Fitted to about 16 columns, CJK characters counting as two; One UI scrolls what does not fit.

### Look
- Each copy takes its app's colour, read from the app's icon: the card's tint and the progress bar. Black and
  white icons keep the system colour.
- Progress shows the app's icon riding the bar.
- The app's icon sits on the card; when a contact photo takes its place, the app's name is shown instead.
- New icon: a dark status bar chip with a lit green indicator, on porcelain white.

### App
- Home screen led by the island: what is on it now, "Waiting" with a test button, or the setup steps still
  missing; below it the apps that are on.
- **Add apps**: about 60 suggested apps (Taiwan and global) grouped by kind with a sample chip each, apps seen
  posting ongoing notifications, then every app, with search. See [docs/apps.md](docs/apps.md).
- A page per app with one main switch, plain choices for what to show, People for chat apps, and skipped words.
  It shows what the app's latest notifications did and why, offers the fix when a one-off was not shown, and
  previews the app's real chip while it is on the island.
- Sensible starting setup per kind of app, and app-specific rules for Gogoro, foodpanda and 55688.
- **Recent activity**: what happened to each recent notification and why, in plain language, without keeping
  notification text.
- Opt-in on-device notification samples for writing app rules.
- One tap turns on every suggested app (or a whole kind) with its starting setup; the home screen suggests apps
  seen with something in progress; recent senders can be picked as people with one tap.
- Per-app kinds of notifications (the app's own channels, by name) can be turned off; kinds named like
  promotions start off. Skipped words can be set once for all apps, with the common promotion words one tap away.
- Pause everything from the menu or a Quick Settings tile.
- Fixed teal-and-ink colour scheme; English and Traditional Chinese.

[Unreleased]: https://github.com/hphbryan/liveisland/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/hphbryan/liveisland/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/hphbryan/liveisland/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/hphbryan/liveisland/releases/tag/v0.1.0
