# Privacy

LiveIsland is designed so that nothing it sees leaves your device.

- **No network access.** The app does not request the `INTERNET` permission, so it cannot send data anywhere.
- **No analytics, crash reporting or ads.**
- **Notification access** is used only to decide, on the device, whether to show a Live Update and what it says.
  Notification content is held in memory while the relayed Live Update is on screen. A relayed copy follows the
  source's lock screen setting, so it never shows more there than the original would.
- **Settings** (which apps are enabled) are stored in the app's private storage and excluded from backups.
- **People** you pick for a chat app (names only) are stored with the settings.
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
- **讀取通知**只用於在手機上判斷要不要顯示即時通知、顯示什麼內容。轉接的通知沿用原通知的鎖定畫面設定，不會比原通知顯示得更多。
- **設定**存在 App 的私有空間，且不會備份。
- **指定的人**（只有名稱）和設定存在一起。
- **最近傳訊息的對象**：只針對你開啟的通訊軟體，保存最近傳訊息的人名或群組名稱（每個 App 最多 20 個，不含訊息內容），
  讓你新增指定的人時可以直接點選。在即時島裡關閉該 App 時會一併刪除。
- **最近活動**只在記憶體保留最近通知的 App、時間與處理結果（最多 100 筆，不含通知內容），App 程序結束或按「清除」就會消失。
- **通知樣本**預設關閉；開啟後只記錄已打開的 App 的新通知，最多 300 則，存在 App 私有空間。只有你自己按「分享」才會離開手機，
  隨時可以按「清除」或解除安裝 App 刪除。
