# Privacy

LiveIsland is designed so that nothing it sees leaves your device.

- **No network access.** The app does not request the `INTERNET` permission, so it cannot send data anywhere.
- **No analytics, crash reporting or ads.**
- **Notification access** is used only to decide, on the device, whether to show a Live Update and what it says.
  Notification content is held in memory while the relayed Live Update is on screen. For a chat, its unread
  messages (up to 10) are kept in memory too, so the relayed copy can show all of them, and forgotten when the
  chat's notification goes away. A relayed copy follows the source's lock screen setting, so it never shows more
  there than the original would.
- **Settings** (which apps are enabled and how) are stored in the app's private storage. They are included in
  Android's own backup and device transfer, so a new phone keeps them; nothing else the app stores is.
- **People** you pick for a chat app (names only) are stored with the settings, so they are backed up with them.
- **Dismiss counts**: how many times you dismissed each app's relayed copies, and of which notification type (its
  id), so LiveIsland can suggest showing less. Numbers only, no notification text, on this phone only (not backed
  up), and cleared for an app once you answer its suggestion.
- **Copy settings** puts an app's choices (one-offs, chip, skipped words, notification type ids) on the clipboard
  as text, only when you tap it. People are never included.
- **Recent senders**: for chat apps you enabled, the names of the latest message senders and groups (at most 20
  per app, no message text) are kept in the app's private storage so you can pick them when adding people. They
  are forgotten when you turn the app off in LiveIsland.
- **Recent activity** keeps the app, time and outcome of recent notifications (up to 100, no notification
  text) in memory only; it is gone when the app process ends or when you tap *Clear*.
- **Notification samples** (off by default) record the text and structure of new notifications from the apps you
  enabled, up to the latest 300, in the app's private storage. They are only shared if you tap *Share* yourself,
  and can be deleted at any time with *Clear* or by uninstalling the app.

## 隱私（繁體中文）

- **沒有網路權限**，無法把資料傳到任何地方。
- **沒有分析、當機回報或廣告。**
- **讀取通知**只用於在手機上判斷要不要顯示即時通知、顯示什麼內容。聊天的未讀訊息（最多 10 則）只放在記憶體，讓轉接的通知展開時能看到全部，
  原通知消失（已讀）就忘掉。轉接的通知沿用原通知的鎖定畫面設定，不會比原通知顯示得更多。
- **設定**（開啟了哪些 App、怎麼顯示）存在 App 的私有空間，會隨 Android 本身的備份與換機轉移帶到新手機；App 存的其他資料都不會。
- **指定的人**（只有名稱）和設定存在一起，所以也會一起備份。
- **移除次數**：記錄你移除每個 App 轉接通知的次數，以及是哪一類通知（類型代號），用來建議少顯示一些。只有數字、不含通知內容，
  只存在這支手機（不備份），回答該 App 的建議後就清除。
- **複製設定**只在你點選時，把 App 的設定（一次性通知、狀態列顯示、略過的字詞、通知類型代號）以文字放到剪貼簿，絕不包含指定的人。
- **最近傳訊息的對象**：只針對你開啟的通訊軟體，保存最近傳訊息的人名或群組名稱（每個 App 最多 20 個，不含訊息內容），
  讓你新增指定的人時可以直接點選。在即時島裡關閉該 App 時會一併刪除。
- **最近活動**只在記憶體保留最近通知的 App、時間與處理結果（最多 100 筆，不含通知內容），App 程序結束或按「清除」就會消失。
- **通知樣本**預設關閉；開啟後只記錄已打開的 App 的新通知，最多 300 則，存在 App 私有空間。只有你自己按「分享」才會離開手機，
  隨時可以按「清除」或解除安裝 App 刪除。
