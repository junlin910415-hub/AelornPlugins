# AelornWorlds 稽核報告（2026-08-17）

對照版本：`AelornWorlds-2.0.0-folia-26.2`，原始碼 15 支 → 14 支（`RegionFanOut` 併入核心）。
建置：`build-all.ps1 -Only AelornWorlds` 通過，已部署到 `plugins\`。

---

## 一、起點：那則警告是誤報

```
[20:40:41 WARN]: [AelornWorlds] 世界 world 在 10 秒內載入了 559 個區塊（門檻 400）；請檢查傳送、生怪或預生成行為。
```

**這則警告本身沒有偵測到任何問題。** 舊版把「區塊載入總數」當成抖動指標，但一位玩家在
視距 10 上線就會載入約 441 個區塊（21×21），預生成與飛行探索只會更多 —— 每一個都是
「載入一次」，成本本來就該付。門檻 400 等於「有人上線就警告」。

真正會拖垮伺服器的是**同一批區塊反覆進出**：傳送迴圈、兩個區塊載入器互相拉扯、
`spawn-chunk-radius` 與世界邊界打架。

### 改法

現在每個視窗改為統計「**重複載入次數**」—— 同一個區塊在同一視窗內第二次以後被載入的總和。

| 情境 | 載入數 | 重複次數 | 舊版 | 新版 |
|---|---|---|---|---|
| 一位玩家上線（視距 10） | ~441 | 0 | ⚠️ 誤報 | 靜默 |
| 預生成 | 數千 | 0 | ⚠️ 誤報 | 靜默 |
| 探索新地形 | 數百 | 0 | ⚠️ 誤報 | 靜默 |
| 傳送迴圈（50 區塊 × 12 次） | 600 | 550 | ⚠️ 報了但看不出原因 | ⚠️ 並指出最頻繁的區塊座標 |

配套：

- **新生成的區塊完全不計入重複統計**（`isNewChunk()`）。預生成因此連 map 都不會成長，
  零額外成本 —— 這也是它不再誤報的根本原因。
- **啟動寬限期** `chunk.churn.startup-grace-seconds`（預設 90）。開機與世界剛載入時會一次
  重播大片區域，那是一次性成本。
- 警告訊息會附上**最頻繁的區塊座標與次數**，通常直接就能定位原因。
- `warn-threshold` 預設 400 → **200**（語意已改為重複次數，不是同一把尺）。
- `/aw chunks` 的「視窗峰值 / 目前視窗」欄位跟著改為重複載入次數；正常遊玩應接近 0。

---

## 二、授權缺失

### 2.1 `/aw health` 是任何玩家都能按的 DoS 開關（高）

指令權限是 `aelorn.worlds.use`，**`default: true`**，而 `/aw health` 會對世界的
**每個已載入區塊各送一個任務**；不帶世界參數時對**所有世界**一起做。沒有冷卻、沒有額外權限。

- 新增 `aelorn.worlds.inspect`（`default: op`）
- 新增 `health.census-cooldown-seconds`（預設 10），以發送者為單位，`merge()` 原子判定

### 2.2 `require-per-world-permission` 會把管理員鎖在門外（中）

每世界權限節點 `aelorn.worlds.world.<世界名>` 是**依設定產生**的，無法在 plugin.yml 宣告成
`admin` 的子節點。所以一旦打開 `require-per-world-permission`，連 `aelorn.worlds.admin`
都會被拒絕，除非有人手動逐一授權每個世界。

- 新增 `Permissions.hasOrAdmin()`，並讓 `entry.bypass` 一併豁免每世界節點
- 傳送介面的「可否點擊」提示同步套用同一條規則（原本會顯示成不可點擊）

### 2.3 admin 節點過度集中（中）

`purge`（不可復原地大量刪實體）、`save`、`border`、`setspawn`、`reload` 全部綁在
`aelorn.worlds.admin`。想讓幹部清一次怪，就得連設定重載一起給。

依「造成的破壞範圍」拆開，並全部宣告為 `admin` 的子節點 —— **既有的 admin 授權完全不受影響**：

| 節點 | 涵蓋 |
|---|---|
| `aelorn.worlds.use` (default true) | menu / status / info / rules / chunks / who / border 唯讀 |
| `aelorn.worlds.inspect` | health（實體普查） |
| `aelorn.worlds.transfer` | tp 自己 / spawn |
| `aelorn.worlds.transfer.others` | tp 其他玩家（原本需要完整 admin） |
| `aelorn.worlds.purge` | purge |
| `aelorn.worlds.save` | save |
| `aelorn.worlds.edit` | border 調整 / setspawn |
| `aelorn.worlds.admin` | apply / reload + 以上全部 |

Tab 補完也改為只列出該發送者實際有權限的子指令。

---

## 三、漏洞與處理方式

### 3.1 進入規則可用「登出再登入」繞過（高）

`entry.player-limit` 與 `entry.permission` 只在傳送與傳送門時檢查。**在受限世界登出、再登入，
伺服器會把玩家放回原處，中間不經過任何規則。** 限時活動世界、人數上限世界的限制因此形同建議。

- `PlayerJoinEvent` 新增進入檢查（`rules.enforce-entry-on-join`，預設開啟）
- 不合規時移到 `rules.entry-fallback-world`（留空則用伺服器第一個世界）
- 登入沒有來源世界，所以只檢查人數與權限，不檢查 origin 規則
- 人數判定加上 `alreadyInside` 修正：登入時玩家已被算進世界人數，否則最後一位合法玩家
  會在下次重登時被自己擠掉（off-by-one）

### 3.2 內部傳送標記可被無關的傳送吃掉（中）

原本是 `Set<UUID>` 的布林旗標：「這位玩家的下一次傳送已驗證過」。問題是**誰先到誰吃掉**。

- 我方的合法傳送被吃掉 → 多做一次檢查，可能誤拒
- **無關的傳送吃掉旗標 → 完全跳過進入檢查**。任何能在傳送進行中觸發第二次傳送的玩家，
  都能用這個窗口進入權限、來源或人數本該拒絕的世界。

改成帶**目的地世界 + 到期時間**的票券，並要求 `TeleportCause.PLUGIN`。不相符的票券刻意不消耗
（它屬於還沒到達的那次傳送），過期即丟。`PlayerRespawnEvent` 跨世界重生也改為發票券。

### 3.3 `config.yml` 寫入競態（中）

`/aw setspawn` 直接改共用的 `getConfig()` 再非同步 `saveConfig()`，而 `/aw reload` 同時會
`reloadConfig()`。兩者交錯可能：把已被取代的設定寫回去、或把重載到一半的文件覆蓋掉管理員
有註解的檔案。安靜失敗，直到有人發現世界定義不見了。

- 新增 `configFileLock`，讀寫路徑全部序列化
- `reloadConfig()` 從 global region 移進非同步區段 —— 它是磁碟 IO，本來就不該在 tick 執行緒上
- Location 的座標在呼叫端先取出，不把 Location 帶進 IO 執行緒

### 3.4 主控台 arrival 指令沒有節流（中）

`arrival.commands` 在 `commands-as-player: false`（預設）時以**主控台身分**執行，
觸發條件是「玩家進入世界」—— 玩家自己控制、可以反覆做。清單裡若有 `give` / `eco add`，
來回走傳送門就是一台發放機。

- 新增每世界 `arrival.command-cooldown-seconds`（預設 5），以「玩家 × 世界」計
- `{player}` 只在名稱是純英數底線時代入；Bedrock 之類含空白/前綴的名稱會把一條指令拆成
  不同參數，改為略過並記錄警告

### 3.5 掉落物上限會讓刷怪塔每次掉落都全區塊掃描（中，效能）

計數器超過上限後，**每一次掉落**都觸發一次完整的 `chunk.getEntities()` + 排序。
長期停在上限的刷怪塔正好是最糟情況。

- 新增 `chunk.item-cap.rescan-cooldown-seconds`（預設 5），同區塊兩次掃描的最小間隔
- 追蹤上限改為「拒絕新增」而非整批 `clear()` —— 原本一清空會讓每個區塊在下次掉落時全部重掃

### 3.6 傳送冷卻的 check-then-act 競態（低）

`get()` 後 `put()` 之間，介面點擊與 `/aw tp` 從不同執行緒同時進來會雙雙通過。改用 `merge()` 原子判定。

### 3.7 生命週期與洩漏（低）

- `onDisable` 沒有 `HandlerList.unregisterAll` 也沒有 `schedulers.cancelAll()`；
  插件管理器重載後，排程仍對著已死的 class loader 觸發
- `metrics` / `lastAutoPurgeAt` 以世界 UUID 為鍵，但從來沒有移除 → 新增 `WorldUnloadEvent` 清理
- `ChunkLoadEvent` 不是 `Cancellable`，`ignoreCancelled = true` 無意義（已移除）
- `game rule` 布林值用 `Boolean.parseBoolean`，`yes` 或打錯字都會**安靜地變成 false**，
  看起來像設定生效了 —— 改為只接受 `true`/`false`，其餘記錄警告並跳過

---

## 四、採用 AelornLib

- **刪除 `RegionFanOut`** → 改用 `Schedulers.forEachLoadedChunk`。兩份實作原本逐字相同，
  核心那份是權威版本
- **傳送介面改用核心 `Menu` 框架** → AelornWorlds 少一個 listener。點擊改由核心註冊的
  單一 inventory listener 路由（取消、標題比對、shift-click 進上層背包這三個反覆重炸的坑
  不再有機會各自炸一次）
- 點擊時**重新從 registry 查世界**，而非沿用開啟當下的快照 —— 開介面與點擊之間若發生
  reload，原本會對著已不存在的設定執行傳送

事件處理現況（14 個處理器）：`ChunkLoad` / `ChunkUnload` / `ItemSpawn` / `WorldLoad` /
`WorldUnload` / `PlayerQuit` / `PlayerTeleport` / `PlayerPortal` / `PlayerCommandPreprocess` /
`PlayerJoin` / `PlayerChangedWorld` / `PlayerMove` / `PlayerRespawn`，
外加核心託管的介面點擊。

---

## 五、未修，需要你決定

### 5.1 AelornLib 原始碼目前**編不過**（阻擋核心重建）

`AelornLib\...\ui\Menu.java` 有三個方法是半成品：

```
storage(@Nullable Predicate<ItemStack>, int...)   // 缺 import、缺 slotFilters 欄位
onPageChange(BiConsumer<Player, Integer>)         // 缺 onPageChange 欄位
onRejected(BiConsumer<Player, ItemStack>)         // 缺 onRejected 欄位
```

`MenuListener` 完全沒有引用 `slotFilters` / `onRejected` / `onPageChange`。

**我刻意沒有「補上欄位讓它編過」** —— 那會產出三個「有 API、但什麼都不做」的方法，
比編譯錯誤更危險：呼叫端會以為欄位過濾器生效了。要真的完成，`MenuListener` 必須在
**四條放入路徑**（游標點擊、數字鍵交換、shift 移入、拖曳）都套用過濾，漏一條就是
「檢查有效直到有人找到另一條路」。

實機跑的 `AelornLib-1.0.0.jar`（8/10 建置）是**舊的、正常的**版本，不含這三個方法，
所以線上沒有問題，AelornWorlds 也是對這份 jar 編譯的。但**核心目前無法重建**。

### 5.2 破壞性指令沒有二次確認

- `/aw purge <world>`（不帶類別）預設含 `ANIMAL`，`keep-per-chunk` 為 0 → 整個世界的動物一次清空，不可復原
- `/aw border <world> size 1` → 世界邊界縮到 1 格，邊界外的人全部開始扣血

兩者現在各自有獨立權限（`purge` / `edit`），但仍沒有確認步驟。要不要加確認是指令契約的改動，
不在這次範圍內，先列出來。

### 5.3 `/aw who` 對所有人可見

`default: true`，會列出每位線上玩家在哪個世界。多數伺服器無妨，但若有隱身或管理員巡邏需求，
可能需要另外拆節點。
