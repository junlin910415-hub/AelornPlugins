# RPGCore：兩棵樹已合併

原本 RPGCore 的原始碼分散在兩棵已分岔的樹，兩棵都建不出正式服在跑的版本。
2026-08-23 已合併為單一棵樹。這份文件保留當時怎麼判斷的紀錄 ——
因為「為什麼選這一邊」比「選了哪一邊」更難重建。

## 合併前

正式服跑 `RPGCore-0.26.0-SNAPSHOT-AELORN-Combat-folia-26.2.jar`，**263 個類別**。

| 樹 | 類別數 | 實機有、它沒有 |
|---|---:|---:|
| `plugin-sources/RPGCore`（0.25.0-Nexo） | 212 | 51 |
| `D:\RPGSystem\RPGCore`（模組系統） | 88 | 175 |

兩棵都是實機的子集，39 個同名檔案裡 **14 個內容不同**。

## 衝突怎麼裁的

拿實機 jar 當基準真相（它是兩棵合併建出來的），用三種獨立判準，
一種比一種更有指認力：

| 判準 | 原理 | RPGSystem 勝 | plugin-sources 勝 |
|---|---|---:|---:|
| 方法集合 | `javap -p` 取實機 class 宣告的方法，看哪棵涵蓋得完整 | 3 | 0 |
| 字串常數 | `javap -c` 取常數池字串，編譯後逐位元保留 | 3 | 0 |
| **編譯器** | 合併後直接編，看誰引用了不存在的東西 | — | **2** |

前兩種判準下 RPGSystem 全勝、其餘平手，所以先一律採它。
**第三種判準推翻了其中兩個**：

```
integration/nexo/CustomItemProvider.java   RPGSystem 版 import integration.oraxen
integration/nexo/HudGlyphProvider.java     同上 —— 那是 Oraxen 時代的舊版
```

這兩個在前兩種判準下是「無法判斷」，編譯器給了答案。**這就是為什麼合併後一定要編一次**：
靜態比對看不出「引用了不存在的套件」。

最終：12 個衝突採 RPGSystem、2 個採 plugin-sources。

## 順便補回一個兩棵樹都缺的方法

`platform/RpgScheduler.java` 少了 `runGlobalAtFixedRate` ——
實機 class **有**，plugin-sources 版沒有，RPGSystem 根本沒這個檔案。
**兩棵樹都比實機舊。**

依實機 `javap` 的確切簽章（吃 `Consumer<ScheduledTask>` 而不是 `Runnable`，
因為週期任務通常要拿到自己的 handle 才能自我取消）與同檔案 `runRegionAtFixedRate`
的既有寫法補回去，包含登記進 `repeatingTasks` —— 漏登記的週期任務會在插件卸載後
繼續對著失效的 class loader 跑。

## 結果：261 / 263

```
實機 263 個類別｜合併後 261 個
實機有、合併後沒有：2
    integration/oraxen/CustomItemProvider.class
    integration/oraxen/HudGlyphProvider.class
合併後有、實機沒有：0
```

缺的那兩個是 **Oraxen 整合**。Oraxen 早已被 Nexo 取代，實機 `plugins/` 底下
只剩一個空的 `Oraxen` 資料夾、沒有 jar —— 那是實機 jar 裡的死程式碼，
兩棵樹都沒有它的原始碼，也不值得為一個已經下架的系統重建。

**所以合併後的樹比實機那個 jar 乾淨**，不是比它少東西。

## 還沒做的

- **沒有逐位元比對**。那需要相同的建置環境與時間戳，不是這次的目標。
  已驗證的是類別集合與建置通過。
- **沒有部署到正式服**。這是 MMORPG 的核心插件，換掉它要有實機測試計畫，
  不是合併完就推上去。要部署的話，`RPGCore-0.26.0-SNAPSHOT_26.2.jar` 在
  `plugins/RPGCore/build/libs/`。
