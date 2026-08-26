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

## 結果：頂層 261 / 263，含內部類別 382 / 388

```
             實機    合併後   缺
頂層類別      263     261      2      <- 早期只量到這一層
所有 .class   388     382      6
```

**只量頂層類別會把內部類別的落差整個藏起來。** 用 `unzip -l` 對整個 jar
逐一比對才看得到真實數字：

```
integration/oraxen/CustomItemProvider.class    死程式碼（見下）
integration/oraxen/HudGlyphProvider.class      死程式碼（見下）
RpgCorePlugin$1.class                          真缺口
RpgCorePlugin$2.class                          真缺口
RpgCorePlugin$3.class                          真缺口
combat/StatService$StatDecorator.class         真缺口
```

### 兩個 Oraxen 是死程式碼

Oraxen 早已被 Nexo 取代，實機 `plugins/` 底下只剩一個空的 `Oraxen` 資料夾、
沒有 jar。兩棵樹都沒有它的原始碼，也不值得為一個已下架的系統重建。

### 四個是真缺口 —— 原始碼比實機舊

| 缺的類別 | 是什麼 |
|---|---|
| `RpgCorePlugin$1` | 匿名 `ReagentService.ExternalReagent` —— 法力／試劑整合接線 |
| `RpgCorePlugin$2` | 匿名 `SkillContext.Hooks` —— 傷害／治療／光環接線 |
| `RpgCorePlugin$3` | 匿名 `SkillCostGate` —— 技能耗費檢查接線 |
| `combat.StatService$StatDecorator` | 公開介面 + `setDecorator()` 擴充點 |

跟 `RpgScheduler.runGlobalAtFixedRate` 同一類：**實機有、兩棵原始碼樹都沒有**。
合併並沒有製造這些缺口，只是讓它們第一次被量出來。

## EquipmentService 已對齊實機（2026-08-26）

原始碼是**重構前**的版本，實機是**重構後**的。實機多出這些：

```
inspectRequirements(ItemStack, CharacterProfile)     公開
report(EquipmentTemplate, int, CharacterProfile)     私有
report(MmoItemsBridge$Identity, CharacterProfile)    私有
appendSkillEntries / appendQuestEntries              私有
summarize(EquipmentRequirementReport)                私有靜態
denialMessage(Entry)                                 私有靜態
```

`summarize` 正是 `EquipmentRequirementReport` javadoc 承諾的那個重構
（「requirements 改為建立在本型別之上」）—— 實機做完了，原始碼樹停在做之前。
舊的 `skillRequirements` / `questRequirements` 兩個 helper 在實機已被刪除。

依 bytecode 補回後驗證：

```
EquipmentService                    83 / 83 成員，只差 oraxen -> nexo
  其中需求相關 19 個方法             完全一致
EquipmentRequirementReport          15 / 15 成員完全一致
EquipmentRequirementReport$Entry    15 / 15 成員完全一致
```

**一個只有實機才看得出來的細節**：兩個 `report()` 都把「職業」排在「戰鬥等級」
之前。順序決定 `firstUnmet()` 挑中哪一項，也就決定玩家看到哪一句阻擋訊息。
先照直覺寫成等級在前，比對 bytecode 常數順序才發現反了。

## 還沒做的

- **沒有逐位元比對**。那需要相同的建置環境與時間戳，不是這次的目標。
  已驗證的是類別集合與建置通過。
- **沒有部署到正式服**。這是 MMORPG 的核心插件，換掉它要有實機測試計畫，
  不是合併完就推上去。要部署的話，`RPGCore-0.26.0-SNAPSHOT_26.2.jar` 在
  `plugins/RPGCore/build/libs/`。
