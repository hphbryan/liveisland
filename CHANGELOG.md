# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[Unreleased]: https://github.com/hphbryan/liveisland/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/hphbryan/liveisland/releases/tag/v0.1.0
