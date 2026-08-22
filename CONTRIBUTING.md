# 貢獻指南

## 開始之前

讀 `docs/CONVENTIONS.md`。所有插件共用同一套骨架，新插件照那個開。

參考實作：
- **Java**：`plugins/AelornWorldEvents`（最小、完整）
- **Kotlin**：`plugins/AelornKotlinRef`（純 Kotlin，零個 `.java`）

## 建置

```powershell
powershell -ExecutionPolicy Bypass -File build-all.ps1 -ServerRoot "D:\你的伺服器" -Only <專案名>
```

需要 JDK 25。建置是**離線**的，但需要一棵伺服器樹提供第三方 jar（見 README）。

## 建置閘門會擋下來的事

這些不是慣例，是**編譯期強制**，違反了會當場建置失敗：

1. **契約層不得引用實作層。** `lib.*` 是契約、`lib.internal.*` 是實作。
   實作型別一旦出現在契約層，依賴插件就被迫連實作一起綁。
2. **版本無關層不得出現伺服器內部型別。** 除了 `nms/impl/v<家族>/` 之外的所有程式碼，
   一律以「classpath 不含伺服器核心」編譯。`net.minecraft` 或 `craftbukkit` 洩漏進來就編不過。
3. **jar 檔名與 `plugin.yml` 的 version 必須逐字相同。** 這兩個曾經漂移過，
   而且是無聲的 —— `/plugins` 顯示一個版本、管理員手上的檔案叫另一個版本。

## 提交前

- 訊息進 `messages.yml`、可調門檻進 `config.yml`，不要寫死
- 註解寫**為什麼**，不要寫程式碼已經說得很清楚的「做什麼」
- 動到跨版本行為的，說明在哪些核心上驗過
- **不要提交任何憑證**：Discord ID、資料庫連線字串、API 金鑰。
  `config.yml` 裡一律留空值並附說明

## 執行緒

Minecraft 伺服器的執行緒模型是這套程式碼最容易出錯的地方：

- **不要用 `Bukkit.getScheduler()`** —— Folia 系上會直接丟例外。走 `AelornLib` 的 `Schedulers`
- **不要用同步 `entity.teleport()`** —— 同上，用 `teleportAsync()`
- **封包處理器跑在 Netty 執行緒上**。碰世界狀態前必須 `view.onPlayerRegion(...)` 跳回去
- **資料層與儲存層的 future 完成在虛擬執行緒上**。同樣，動世界前要跳回 region
