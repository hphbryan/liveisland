# Supported apps

LiveIsland can relay **any** app: open **Add apps** on the home screen and turn it on. The apps
below are only *suggestions* — they are listed up front when installed because they are known or
expected to post ongoing notifications (something in progress) and, as of November 2026, do not post
Live Updates of their own.

Every package name was checked against its Google Play listing. Whether an app's notification is
really ongoing is marked as:

- **confirmed** — known to post this kind of notification, from testing or the app's own documentation;
- **expected** — the usual behaviour for this kind of app (a foreground service while recording,
  navigating or uploading), not yet confirmed for this app;
- **static** — found by inspecting the app's published package (its manifest, notification strings or
  code), not yet confirmed in use;
- **unknown** — please try it and report back (see [CONTRIBUTING.md](../CONTRIBUTING.md)).

## AI assistants (one-off replies, shown briefly)

| App | Package | Status |
|---|---|---|
| ChatGPT | `com.openai.chatgpt` | unknown |
| Claude | `com.anthropic.claude` | **confirmed** (reply and Claude Code notifications) |
| Gemini | `com.google.android.apps.bard`, `com.google.android.googlequicksearchbox` (Gemini channels only) | **confirmed** |
| Grok, Copilot, Perplexity | `ai.x.grok.bot`, `com.microsoft.copilot`, `ai.perplexity.app.android` | unknown |

## Chat apps

LINE, WhatsApp (and Business), Messenger, Instagram, Telegram (and Telegram X), Signal, Discord, KakaoTalk,
WeChat, Viber, Snapchat, Slack, Teams, Google Messages, Samsung Messages, Google Chat. Messages are matched to
**people** by the sender, the group name or the title of the notification; chat apps that use Android's
conversation format (MessagingStyle) report the sender most reliably. Status: unknown per app, please report.

## Taiwan

| Category | App | Package | Status |
|---|---|---|---|
| Delivery | foodpanda | `com.global.foodpanda.android` | **static**: order progress comes as one-off pushes (channel "訂單進度更新"), so flash is on by default, with common promotion words skipped |
| Taxi | 55688 台灣大車隊 | `dbx.taiwantaxi` | **static**: ride notifications are pushes; flash on by default, promotion words skipped |
| Taxi | LINE GO, TaxiGo, yoxi, 大都會計程車 | `com.taxigo.rider`, `com.taxigo.rn`, `tw.com.yoxi.rider`, `com.tmc.mtaxi` | unknown |
| Sharing | YouBike | `tw.com.youbike.plus` | unknown (Flutter app; nothing found in its resources) |
| Sharing | GoShare, WeMo, iRent | `com.gogoro.goshare`, `com.wemoscooter`, `com.cht.easyrent.irent` | expected |
| Transit | 台北公車通 | `tw.skystar.tpbus` | unknown (bus arrival reminders) |
| Transit | 台北捷運Go | `tw.com.trtc.is.android05` | **static**: next-station reminder runs as a location foreground service |
| Transit | 台北等公車, 高雄iBus+ | `nexti.android.bustaipei`, `com.foxconn.ibusAndroid` | unknown |
| Scooter | Gogoro | `com.gogoro.smartclient` | **static**: only its charging notification ("充電中", time left) is relayed; its permanent connection notification is skipped |
| Charging | U-POWER, EVOASIS | `com.u_power`, `com.tw.evoasis.cpms.app` | unknown |
| Parking | USPACE, 易停網, Times | `com.uspace`, `com.app.uparking`, `tw.com.park24.parkingapp` | unknown |

## Global

| Category | Apps | Status |
|---|---|---|
| Rides | Bolt, DiDi | expected |
| Delivery | DoorDash, Deliveroo, Grubhub, Instacart, Glovo, Bolt Food, Swiggy | expected |
| Navigation and transit | Waze, OsmAnd, Organic Maps, Citymapper, Transit, Moovit, Lime | expected |
| Workouts | Strava (**confirmed** recording notification), Nike Run Club, adidas Running, Runkeeper, komoot, Google Fit, Hevy, Strong | expected |
| Sleep and focus | Forest (**static**: "你的小樹正在努力長大…", countdown or count-up time), Sleep as Android, Sleep Cycle, Focus To-Do | expected |
| Transfers | Google Drive, OneDrive (**static**: upload foreground services), Dropbox, ADM | expected |
| Charging and parking | Tesla, ChargePoint, ParkMobile, PayByPhone, EasyPark | unknown |

The exact package names are in [`KnownApps.BY_CATEGORY`](../app/src/main/kotlin/io/github/hphbryan/liveisland/core/KnownApps.kt).

## How the static checks were done

The published packages (APKs) of several suggested apps were inspected without running them:
foreground-service types and `POST_PROMOTED_NOTIFICATIONS` in the manifest (`aapt2 dump permissions`,
`aapt2 dump xmltree`), notification and channel strings (`aapt2 dump resources`), and notification API
references in the code. This shows what an app *can* post, not what it posts in a given situation, so
these entries move to **confirmed** only after the notification is seen in use.

## Not suggested

- **Already post their own Live Updates**, which LiveIsland leaves alone: Google Maps (navigation),
  Uber, Uber Eats, Lyft, Grab, Zomato, Just Eat Takeaway, and — judging by their packages, which hold
  `POST_PROMOTED_NOTIFICATIONS` and request promotion — Bus+ and Google Photos. Samsung's own apps use
  Samsung Live Notifications and are skipped too.
- **One-off pushes only** (shopping and parcels, travel, mail, alerts): they never show something in
  progress, which is what LiveIsland is for. You can still add them by hand.
- **Mostly promotional notifications**: convenience-store, fast-food and airline apps. You can still
  add them and add **skipped words** on the app's page to filter out promotions.
- Media players, calls and VPNs are never relayed.
