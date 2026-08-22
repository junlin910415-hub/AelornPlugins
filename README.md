# 艾洛恩 AELORN — Minecraft 插件

為 **Purpur / Paper / Folia 26.2** 開發的一組 MMORPG 伺服器插件，全部離線編譯，
不需要 Gradle 或 Maven。核心是 **AelornLib**，其餘插件都建在它上面。

---

## 這裡有什麼

### 基礎層

| 插件 | 說明 |
|---|---|
| **AelornLib** | 全站共用的 API 集合。**沒有任何遊戲功能** —— 只提供機制。平台識別、執行緒守衛、排程、文字、設定解析、四層 NMS 介接、**自建封包管線**、選單框架、資料層（連線池＋快取）、文件儲存層（YAML / SQL / MongoDB / 記憶體）。 |
| **AelornLibKt** | AelornLib 的 Kotlin 擴充面。文件 DSL、查詢 DSL、型別推導的具名讀取。**可選** —— Java 插件完全不需要它。 |
| **AelornKotlinRef** | 純 Kotlin 插件的參考實作。整個專案零個 `.java`。 |

### 功能插件

| 插件 | 說明 |
|---|---|
| **RPGCore** | MMORPG 核心：角色、職業、任務、發現、遭遇、成長、HUD、技能。 |
| **AelornStore** | 儲值與商店系統。金流適配層刻意留空待選商。 |
| **AelornWorlds** | 世界管理：視覺優化、區塊優化、世界傳送。 |
| **AelornWorldEvents** | 世界事件與遭遇生成。 |
| **AelornBackpack** | 背包系統。 |
| **AelornHolograms** | 浮空文字。 |
| **AelornQuestBridge** | RPGCore 任務的 PlaceholderAPI 橋接。 |
| **AelornDiscordBridge** | DiscordSRV 安全橋接。 |
| **RPGCoreMythicBridge** | RPGCore ↔ MythicMobs 橋接。 |
| **ServerBackup** | 伺服器備份。 |
| **PluginsManager** | 插件熱重載與管理。 |

---

## AelornLib 值得單獨說

它有幾個設計決定與常見做法不同，理由都寫在對應的 javadoc 裡：

**自建封包管線。** 不依賴 ProtocolLib 或 PacketEvents（兩者仍可作為後端）。
封包名稱**從伺服器自己的類別推導**，不是查一張手維護的對照表 ——
那兩個函式庫對同一個封包的命名有相當比例不同（實測：送出方向 85/168 同名、
接收方向 31/75 同名，而分歧的正是移動、挖掘、視窗點擊、聊天這些最常攔截的）。
猜錯名字的後果是註冊一個**永遠不觸發**的監聽器，完全無聲。

**四層 NMS 逐能力降級。** 直編 adapter → MethodHandle → 反射 → 純 Bukkit API，
每個能力各自解析。缺 adapter 只是慢一點，不會讓伺服器起不來。

**文件儲存層。** 同一組 API 之下有 YAML / SQL / MongoDB / 記憶體四個後端，
換後端是一行設定。**後端不可用時是拋例外，不是安靜降級** —— 降級到記憶體會產生
「跑得好好的、重開就全沒了、日誌裡什麼都沒有」，那是儲存層最糟的失敗。

**分層由編譯器強制。** `lib.*` 是契約層、`lib.internal.*` 是實作層，
建置腳本會擋下契約層對實作的引用；版本無關層一律以「不含伺服器核心」的 classpath 編譯，
型別洩漏當場編不過。

---

## 怎麼建

建置**完全離線**，但需要一棵 Minecraft 伺服器樹提供無法進版控的第三方二進位檔：

```
<你的伺服器>/
├── libraries/                        編譯用的 API 與函式庫（Paper 自動下載的那些）
├── versions/26.2/purpur-26.2.jar     NMS adapter 的編譯目標
├── versions/26.2/folia-26.2.jar      可選：第二核心閘門
└── plugins/                          ProtocolLib、PacketEvents、PlaceholderAPI…
```

```powershell
powershell -ExecutionPolicy Bypass -File build-all.ps1 -ServerRoot "D:\你的伺服器"
powershell -ExecutionPolicy Bypass -File build-all.ps1 -ServerRoot "..." -Only AelornLib
```

需要 **JDK 25**（AelornLib 與 PluginsManager 走 `--release 25`，其餘 21）。
Kotlin 專案另外需要 kotlinc，路徑在 `build-all.ps1` 頂部。

單一插件也可以自己建：`plugins/AelornLib/build.ps1` 會自己往上找伺服器樹。

---

## 相依關係

```
AelornLib  ←── 其他所有插件（depend）
    ↑
AelornLibKt ←── 純 Kotlin 插件（depend）
```

AelornLib 的 `plugin.yml` 有 `provides: [AelornCore]`（舊名遷移橋）。
Kotlin 插件**不必自己宣告 kotlin-stdlib**，`depend: [AelornLibKt]` 就會拿到。

---

## 開發慣例

見 `docs/CONVENTIONS.md`。幾條最常被違反的：

- **訊息進 `messages.yml`、門檻進 `config.yml`**，不要寫死在程式碼裡
- **版本號 `<版本>_<MC家族>`**，jar 檔名與 `plugin.yml` 由建置閘門強制一致
- **不要用 `Bukkit.getScheduler()`** —— Folia 系上會丟例外。走 `AelornLib` 的 `Schedulers`
- **封包處理器跑在 Netty 執行緒上**，碰世界狀態前一定要跳回 region

---

## 授權

**尚未選定。** 見 `LICENSE`。在選定之前，這份程式碼沒有授予任何使用權限。

---

## 沒有包含在這裡的東西

兩支插件**刻意排除**，因為它們是從**商業付費插件反編譯**出來的產物，不是手寫原始碼：

- `AelornItems` — 由 MMOItems 衍生
- `MythicCore` — 由 MythicMobs 系衍生

散布這些會侵犯原作者的著作權，公開或私下都一樣。它們留在原本的伺服器樹裡。
