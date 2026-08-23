# RPGCore：兩棵樹，都不完整

**開源前請先讀這份。** RPGCore 的原始碼目前分散在兩棵已經分岔的樹裡，
兩棵都建不出正式服在跑的那個版本。這不是搬運疏漏，是這份程式碼本來的狀態。

## 量出來的數字

正式服跑的是 `RPGCore-0.26.0-SNAPSHOT-AELORN-Combat-folia-26.2.jar`，
主類別 `com.xuzhihuanjing.rpgcore.RpgCorePlugin`，反編譯後有 **263 個類別**。

| 樹 | 類別數 | 實機有、這棵沒有 | 這棵有、實機沒有 |
|---|---:|---:|---:|
| `plugins/RPGCore`（0.25.0-Nexo） | 212 | **51** | 0 |
| `plugins/RPGCore-modules`（原 `D:\RPGSystem\RPGCore`） | 88 | **175** | 0 |

兩棵都是實機的**子集**。212 + 88 − 39（重疊）= 261 ≈ 263，
所以正式服那個 jar 是**兩棵合併**建出來的。

## 為什麼不能直接合併

兩棵有 39 個同名檔案，其中 **14 個內容不同**：

```
combat/DamagePipeline.java
combat/TrainingWeaponService.java
config/MonsterRegistry.java
hud/AeloriaCombatHudRenderer.java
integration/mmoitems/MmoItemsBridge.java
…（共 14 個）
```

盲目覆蓋會產生一棵「兩邊都不是」的樹。哪一版才是現行的，只有作者知道 ——
這不是工具能判斷的事。

## `RPGCore-modules` 是什麼

`D:\RPGSystem\RPGCore` 那一棵，49 個 `plugins/RPGCore` 沒有的類別，
主要是 `api/module/*` 的模組系統（`ModuleContext`、`ModuleDescriptor`、
`ModuleHost`、`ModuleRegistration`…）。看起來是較新的重構方向，
`plugin.yml` 的版本是 `__BUILD_VERSION__` 佔位符（建置時代入）。

它**沒有**進 `build-all.ps1` 的專案表 —— 因為它單獨建不出可用的插件。

## 建議的處理順序

1. 決定那 14 個衝突檔案各以哪一棵為準
2. 把 `RPGCore-modules` 併進 `RPGCore`，或反過來
3. 確認合併後的類別集合涵蓋實機那 263 個
4. 建置、比對、部署，然後把 `RPGCore-modules/` 從倉庫移除

在這之前，`plugins/RPGCore` 建得出來的是 **0.25.0**，不是正式服在跑的 0.26.0。
