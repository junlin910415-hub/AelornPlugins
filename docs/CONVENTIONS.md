# 艾洛恩插件 — 程式碼結構規範

所有第一方插件共用同一套骨架。新插件照這個開,舊插件整理時往這個收斂。

參考實作:**AelornWorldEvents**(最小、完整)與 **AelornWorlds**(完整功能)。

---

## 1. 套件命名

```
tw.linsy.aelorn.<module>
```

`<module>` 是小寫單字:`core`、`worlds`、`worldevents`、`store`、`items`…

不要用 `tw.linsy.aelorn<module>`(黏在一起)、`net.aelorn.*`、`tw.aelorn.*`、`dev.*`。
**例外**:對外 API 相容層保留原命名空間(例如 AelornHolograms 的
`eu.decentsoftware.holograms`),那是契約,改了會斷。

## 2. 目錄分類

```
<Module>Plugin.java        啟動與接線。只做「建物件、註冊、排程」,不含業務邏輯
config/                    設定快照。一次解析成 immutable record,執行期不再讀 YAML
model/                     領域資料。record 為主,無行為或只有純函式
service/                   行為。一個 service 一個職責,建構子拿 plugin
listener/                  Bukkit 事件監聽。只做過濾與轉派,邏輯留在 service
command/                   指令。只決定「呼叫哪個 service、送哪個訊息鍵」
api/                       對其他插件公開的介面(可選)。沒有對外需求就不要建
```

判準:**如果一個檔案同時在解析設定、跑邏輯、又送訊息,它就該被拆開。**

## 3. 一律使用 AelornLib,不要自己造

| 需求 | 用這個 | 不要自己寫 |
|---|---|---|
| 玩家可見文字 | `core.text.Messages` + `messages.yml` | Text / Texts / TextFormatter / Lang |
| 設定解析 | `core.config.ConfigParse` | ConfigParse / ConfigFiles |
| Folia 排程 | `core.sched.Schedulers` | SchedulerBridge / Sync / 裸排程呼叫 |
| 伺服器內部 | `core.nms.Nms` / `NmsBridge` | 直接 cast CraftBukkit |

plugin.yml 要寫 `depend: [AelornLib]`。

## 4. 不寫死

- 玩家看得到的字 → `messages.yml`,程式碼只出現訊息**鍵名**。
  enum(如操作結果)存鍵名,不存文字。
- 數值門檻、材質、槽位、權限節點前綴、指令樣板的 token 清單 → `config.yml`。
- log 與例外訊息可以留在程式碼裡,那不是玩家可見文字。

判準:**改一個字要不要重新編譯?要的話就是寫死了。**

## 5. Folia 規則

- **絕不使用** `BukkitRunnable`、`Bukkit.getScheduler()`、`world.getEntities()`。
- 跨區域掃描用 `RegionFanOut` 那套(快照座標 → 逐塊排程 → 最後一塊回呼)。
- 碰玩家先跳到該玩家的 EntityScheduler;碰世界層級狀態用 global。
- 需要「別的玩家現在在哪」時不要跨執行緒讀 `getLocation()`,
  用移動事件維護的位置快照(見 AelornWorldEvents 的 `PlayerTracker`)。

## 6. 無伺服器核心為主

一般程式碼編譯時 classpath **不含伺服器核心**,建置輸出會標 `無核心`。
伺服器內部只有 AelornLib 的 `nms/impl/v<版本>/` adapter 摸得到,
而且這條規則由編譯器強制——洩漏會當場編不過。

換 Minecraft 版本 = 新增一個 adapter 類別 + `build-all.ps1` 的 `Adapters` 加一行。

## 7. 改名既有插件的三層相容

改名會默默弄壞正式服,以下都不能省(細節見 AelornWorlds):

1. **設定資料夾** — 首次啟動從舊資料夾複製 `*.yml`(複製不搬移,舊的留作回退)
2. **權限節點** — 舊節點在 plugin.yml 宣告為新節點的父節點,程式碼另做執行期回退
3. **指令別名** — 舊指令名留在 `aliases`

---

## Kotlin 插件

**可以寫成純 Kotlin，整個專案一個 `.java` 都不用。** Bukkit 只認 class 檔。
參考實作：`AelornKotlinRef`（不部署到正式服，就像 Java 那邊的 AelornWorldEvents）。

專案佈局：`src/main/kotlin/`（有這個目錄 build-all.ps1 就會編它），
`build-all.ps1` 的專案表加 `KotlinExt = $true` 以取得 `AelornLibKt` 的編譯期 classpath。

三件與 Java 專案不同、而且都不是選配的事：

1. **不要自己宣告 kotlin-stdlib。** 寫 `depend: [AelornLibKt]` 就好 —— 那支已經宣告了,
   Paper 的插件 classloader 看得到相依插件的 classpath。少一份版本號要維護,
   啟動時也少一次解析。已實測。

2. **匯入用 `import tw.linsy.aelorn.kt.*`。** operator 擴充（`contains` 等）必須逐一匯入
   才會被解析,漏一個的症狀是型別推導失敗並把錯誤擴散到後面幾行。

3. **不要期待 `store["k"]` 這種寫法。** Kotlin 的成員永遠優先於擴充,而 `Store` 與
   `Document` 在 Java 那側都已經有名為 `get` 的成員 —— Java 的 `get`/`set` 在 Kotlin
   會自動被當成陣列存取運算子,所以擴充版永遠打不中。同步存取請用具名的
   `load` / `save` / `drop`。

編譯順序是 **Java 先、Kotlin 後**:kotlinc 讀得懂已編好的 `.class`,javac 讀不懂 `.kt`。
所以 Kotlin 可以呼叫同專案的 Java,反向不行。
