# AelornLib 1.0.0

艾洛恩全站共用的 API 集合。**沒有任何遊戲功能** —— 只提供機制，功能一律留在各自的插件裡。

- 目標核心：**Purpur 26.2**（單一主執行緒，正式服）與 **LightingLuminol / Folia 26.2**（區域執行緒），同一份 JAR
- 建置與執行都用 **JDK 25**，`--release 25`（class 69，正好是伺服器 JDK 的上限）
- 82 個檔案，`load: STARTUP`，依賴插件寫 `depend: [AelornLib]`

---

## 1. 分層規則

| 套件 | 規則 |
|---|---|
| `core.*` | **契約層**。其他插件只 import 這裡；簽章不得出現伺服器內部型別 |
| `core.internal.*` | **實作層**。只有 `AelornLibPlugin`（接線點）可以引用 |
| `core.nms.impl.v<家族>` | **唯一**能寫 `net.minecraft` / `craftbukkit` 型別的地方 |

這三條由建置腳本強制，不是靠記性：

1. 非 adapter 的程式碼一律以「classpath 不含伺服器核心」編譯 —— 型別洩漏當場編不過
2. 掃描**全部**原始碼（含 adapter）是否引用 `core.internal`，比對 FQN 本身並排除註解行，
   所以 `import static` 與完全限定名稱都擋得到
3. 磁碟上有 adapter 目錄卻沒登記在 `Adapters` → 建置失敗（否則會靜默不編進 JAR）

adapter 對 **Purpur 與 Folia 各編一次**。第一份進 JAR，第二份只當閘門 ——
目的不是產生兩份 class，而是證明「同一份 adapter 對兩個核心都成立」。

> Netty 是唯一被允許出現在版本無關層的第三方型別。它不是伺服器內部型別（每個核心都有，
> 4.1 與 4.2 的 `Channel` / `ChannelDuplexHandler` 是同一份 class），自建封包後端需要它。

---

## 2. 為什麼核心「降級」而不是「拒絕啟動」

0.1.0 在找不到 NMS adapter 時拒絕啟用，理由是「區域歸屬檢查本身是 NMS 呼叫，答錯會默默弄壞世界」。

**那個前提現在不成立了。** `RegionGuard` 全部改用 `Bukkit.isOwnedByCurrentRegion` 與
`isPrimaryThread` —— 純 Bukkit API，Paper / Purpur / Folia 全系都有且語意正確
（在單執行緒核心上它就等於「是不是主執行緒」，一樣是對的問題）。

安全性不再依賴 NMS，剩下的（封包、raw handle）不值得用整台伺服器不啟動去換。所以：

| 狀況 | 結果 |
|---|---|
| 26.2.7 修補版 | 同家族，adapter 照用，零改動 |
| 26.3 但 adapter 還沒寫 | adapter 婉拒，MethodHandle 層頂上，伺服器照跑 |
| 換成 Folia 系 | 完全相同 —— adapter 對兩個核心各編一次當閘門 |
| 分支核心藏了連線欄位 | 只有自建封包後端掉到函式庫後端，其餘照常 |

---

## 3. 子系統

每個都是同一套模式：**探測 → 取最好的 → 安靜降級 → 診斷指令說得出是哪一層**。

### `nms` — 四層逐能力解析

`ADAPTER`（直編）→ `HANDLE`（MethodHandle）→ `REFLECT`（反射）→ `API`（純 Bukkit）。

同一台伺服器同時用到多層是正常的。HANDLE 層用 `MethodHandles.filterArguments` 把
`entity → getHandle() → chunkPosition() → x()` 組成**單一** handle，`invokeExact` 全程不裝箱。

`ServerMembers` 不用硬編類別名找東西 —— 從 Bukkit 給的物件出發跟著回傳型別走，
所以改類別名沒差，只有改**成員**名才要動候選表。找 `getHandle()` 時取**回傳型別最具體**
的那一個：`CraftPlayer` 與繼承來的 `CraftHumanEntity` 都有同名方法，取錯的症狀只有啟動
日誌裡一行「找不到連線欄位」，功能會安靜降一層。

目前 13 項能力，其中 `player-channel` 與 `protocol-version` 是自建封包後端的前提，
`entity-by-id` 是封包處理器最常缺的那一個（封包只用網路 ID 指實體，Bukkit 只能用 UUID 找）。

`entity-by-id` 刻意**沒有** `Nms` 的守衛版本：擁有實體的 region 要先找到實體才知道，
守衛只能跑在它本該保護的那次讀取之後。要用就自己先跳到對的 region。

### `packet` — 核心自己做，函式庫變成備援

> **這推翻了先前的決定。** 舊註解寫著「核心不該自己做封包攔截，那是 ProtocolLib 與
> PacketEvents 已經解決的大問題」。在「唯一選項是自己寫 channel initializer」時那是對的。
>
> 現在不是了：Paper 提供 `ChannelInitializeListenerHolder`，把 handler 放進每條連線
> 是一次受支援的呼叫，而不是 hack。而拿到的是**伺服器自己的封包物件**，
> 於是封包**名稱可以從伺服器推導**，不必再靠任何人手工維護的對照表。

三個後端，優先順序固定：`AELORN` → `PACKET_EVENTS` → `PROTOCOL_LIB`，
`config.yml` 的 `packet.providers` 是允許清單而不是順序。三者都不可用時退回核心自己送包
（只失去攔截與注入）。

**自建後端獨有的能力：**

| 能力 | 說明 |
|---|---|
| 名稱由伺服器推導 | `ClientboundSetEntityDataPacket` → `SET_ENTITY_DATA`，規則而非清單 |
| `PacketStructure` | 讀寫封包欄位；record 封包自動以正規建構子重建 |
| `PacketFactory` | 用型別名建構封包，不指名伺服器類別 |
| `PacketValues` | 把 Bukkit 值轉成建構子要的伺服器型別（座標／物品／方塊狀態／文字） |
| bundle 展開 | bundle 裡的封包各自進監聽器，取消後自動重組 |
| 從第一個封包開始 | 連線被接受時就掛上，涵蓋登入與設定階段，而不是等玩家進伺服器 |

**型別名不再需要猜。** 舊的兩個函式庫對同一個封包的拼法只有 85/168（送出）與
31/75（接收）一致，而分歧的正是移動、挖掘、視窗點擊、聊天這些最常攔截的。
現在正規名稱來自伺服器本身，`PacketNames` 另外收兩個函式庫的舊拼法當別名，
**三個後端都吃同一組名稱**：

```java
// 這一行在三個後端下都會命中，寫哪一種拼法都可以
PacketListen.on(plugin).outgoing()
    .types(PacketTypes.Outgoing.SET_ENTITY_DATA)   // 或舊的 "ENTITY_METADATA"
    .handle(view -> { ... });
```

註冊時若名稱在這台伺服器上不存在，會**當場警告並指出用什麼指令查** ——
舊行為是註冊一個永遠不觸發、也完全無聲的監聽器。

**改封包欄位**（多數協定封包是 record，改不動，只能重建）：

```java
view.structure().ifPresent(fields ->
    view.replacePacket(fields.set("entityId", replacement).apply()));
```

`apply()` 回傳「該用哪個封包」：能就地寫入時是原物件，record 則是新物件。
`replacePacket` 傳回原物件是 no-op，所以永遠可以無條件呼叫。

> PacketEvents 後端沒有封包物件（它讀的是位元組緩衝），所以 `structure()` 在它底下是空的。
> 這是「不可用」而不是「部分可用」，`provider()` 問一次就知道。

### `input` — 封包欄位 → 具名意圖

```java
PlayerInputs.on(plugin).swapOffhand().cancelVanilla()
    .handle(i -> {
        if (!isWeapon(i.heldItem())) return;              // 便宜的判斷留在封包執行緒
        i.onPlayerRegion(plugin, () -> pages.next(i.player()));  // 動世界才跳 region
    });
```

### `data` — Hikari + 快取 + 虛擬執行緒 + 批次

- 自建 `SimpleDriverDataSource` 繞開 `DriverManager` —— Paper 把驅動放進插件自己的
  classloader，`DriverManager` 看不到。這是 AelornStore 當初手寫連線池的真正原因。
- 快取**單飛**：熱鍵過期時 64 個併發請求只打一次資料庫（已實測）。
- 未來完成的 future 跑在虛擬執行緒上 —— **動世界前一定要跳回 region**。

### `ui` — 真實介面為底，封包只做 Bukkit 做不到的事

全虛擬容器會長出複製漏洞（客戶端與伺服器對某格不一致時沒有權威副本可校正）。
所以底層是真的 `Inventory`，封包只用來**不關閉就改標題**（同 window id 重送開窗封包）。

即時換標題**不再需要 PacketEvents**：核心用 `PacketFactory` 自己建 `OPEN_SCREEN`，
選單型別常數與 Adventure→原生文字轉換都從伺服器讀。PacketEvents 的包裝類別留作備援，
兩條都不成立時才退回關閉重開 —— 會閃，但絕不靜默略過。

點擊路由集中在核心的一個監聽器，處理三個每個插件都會各自重犯的坑：
從玩家背包 shift 點擊、拖曳（是另一個事件）、數字鍵交換。

---

## 4. 診斷

```
/aelornlib info                      平台、核心狀態、NMS 摘要、封包提供者
/aelornlib nms                       逐項能力由哪一層提供
/aelornlib packets [out|in] [關鍵字]  這台伺服器的封包型別實際叫什麼（含別名）
/aelornlib fields <out|in> <型別>     某個封包的欄位版面 —— 寫欄位前先看這個
/aelornlib selftest                  實際跑一遍各子系統，不是印設定值
/aelornlib data                      已開啟的連線池與快取
```

`selftest` 與 `info` 的差別是刻意的：`info` 印的是**啟動時解析出什麼**，
`selftest` 是**現在真的跑得動嗎**。兩者會不一致 —— 能力綁得起來但第一次呼叫就丟例外、
型錄掃到空 JAR、欄位版面讀不出來，都只有後者看得到。

---

## 5. 驗證（2026-08-18）

封包層的失敗模式是**無聲的** —— 監聽器永遠不觸發、欄位寫進去沒生效、訊息鍵解析成空字串，
沒有例外也沒有日誌。所以「建置過了」不算驗過。以下每一項都是實際跑出來的。

### 5.1 建置期：對三份伺服器 JAR 直接跑（不需要伺服器）

`BackendProbe` 以 `AelornLib/build/classes` + 伺服器 JAR + 整個 `libraries\` 樹當 classpath：

| 伺服器 JAR | 封包型錄 | 欄位版面 | record 可重建 | 別名建構 |
|---|---|---|---|---|
| Purpur 26.2 | 232 | 232/232 | 116/116 | 通過 |
| **Purpur 26.1.2**（另一個版本家族） | 232 | 232/232 | 116/116 | 通過 |
| **Folia 26.1.2**（另一個分支 + 另一個家族） | 232 | 232/232 | 116/116 | 通過 |

同時驗證 Paper 連線註冊表綁定（`callListeners` 真的叫得到 Proxy）、
record 重建後原物件不變、bundle 展開與重組。

### 5.2 隔離冒煙伺服器（Purpur 26.2，只掛 AelornLib + 探針）

用手寫的 Minecraft 協定客戶端（VarInt 框架，封包 id 從伺服器 JAR 的 `*Protocols`
類別讀出來，不是猜的）**走完離線登入直到 PLAY 階段**——伺服器日誌確認
`AelornProbe joined the game / logged in with entity id 1`。

| 驗證項 | 結果 |
|---|---|
| 攔截涵蓋的協定階段 | **handshake → login → configuration → play** 全程 |
| 攔截到的封包種類 | 送出 **48 種**、接收 5 種 |
| 高頻封包 | `MOVE_ENTITY_POS` ×853、`SET_ENTITY_MOTION` ×707、`ROTATE_HEAD` ×681、`SET_TIME` ×314 |
| **bundle 展開** | `BUNDLE` ×68，對應 `ADD_ENTITY` ×68 —— 沒展開的話只會看到前者 |
| **欄位讀取** | **3624 次，零例外**（每個攔截到的封包都跑一次 `PacketStructure`） |
| 別名監聽 | 以舊拼法 `"HANDSHAKE"` 註冊的監聽器命中 |
| 封包建構 | 以舊拼法 `"BLOCK_CHANGE"` 建出 `ClientboundBlockUpdatePacket`，欄位正確 |
| 即時換標題 | 可用（走核心自建路徑，未安裝任何封包函式庫） |

`MOVE_ENTITY_POS`、`ROTATE_HEAD`、`SET_ENTITY_DATA` 正是兩個函式庫拼法不同的那些。

### 5.3 關掉直編 adapter：「26.3 出來但 adapter 還沒寫」那一天

把 `nms.strategies` 改成只留 `HANDLE, REFLECT`：

**2026-08-18 在正式伺服器上複驗過一次**（31 支插件、LightingLuminol 26.2，
不是隔離冒煙環境）：13 項能力全部改由 MethodHandle 提供、自檢八項全綠、
31 支插件零載入失敗。啟動日誌逐項列出是哪個成員綁上的
（`ServerPlayer#connection → channel`、`Entity#chunkPosition` …），
所以「降級後還能跑」不是推論，是看得到的。

| 驗證項 | 結果 |
|---|---|
| 13 項 NMS 能力 | **全部由 MethodHandle 層提供**，零降級註記 |
| 自建封包後端 | 照常運作（它只需要 `player-channel`，那也是 MethodHandle 給的） |
| 攔截、欄位讀取、封包建構 | 與有 adapter 時完全相同 |

這條路先前是壞的，而且症狀只有啟動日誌裡一行「找不到連線欄位」：
`ServerMembers` 找 `getHandle()` 時取到繼承自 `CraftHumanEntity` 的那一個
（回傳基底玩家型別）而不是 `CraftPlayer` 的（回傳 `ServerPlayer`），於是接下來
在錯的類別上找連線欄位。現在一律取**回傳型別最具體**的那一個。

### 5.4 實機：31 支插件、LightingLuminol 26.2

正式伺服器跑的其實是 **LightingLuminol 26.2（區域執行緒）**，不是 adapter 的編譯目標 Purpur：

| 驗證項 | 結果 |
|---|---|
| 13 項 NMS 能力 | **全部由直編 adapter 提供** —— 對 Purpur 編出來的那份，在 Folia 系分支上照用 |
| 封包提供者 | `AelornLib 原生`，**勝過同時安裝並已啟用的 ProtocolLib 5.5.0 與 packetevents 2.13.0** |
| 封包型錄 | 232 種（掃描 `folia-26.2.jar`） |
| `selftest` 八項 | 全部通過 |
| `fields out ENTITY_METADATA` | 舊別名解析到 `SET_ENTITY_DATA`，印出真實版面 |
| `packets in dig` | 找到 `PLAYER_ACTION`（別名 `BLOCK_DIG`、`PLAYER_DIGGING`） |
| 即時換標題 | 可用（核心自建 `OPEN_SCREEN`） |
| 其他 30 支插件 | 全部正常啟用；`PluginsManager` 認得核心的排程、文字與選單框架 |

這一輪也抓到一個影響**全站每一支插件**的既有 bug：`Messages` 宣稱新增的鍵會
從內建預設值解析，但實作用了 Bukkit 的兩參數 `getString(key, "")` ——
那個多載**不查 defaults**。症狀是 `/aelornlib selftest` 在舊 `messages.yml` 的
伺服器上完全沒有輸出，沒有錯誤、沒有日誌。已修為單參數版本，並在同一台
（`messages.yml` 仍是舊的）伺服器上確認新鍵確實從內建副本解析出來。

---

## 6. 與 ProtocolLib 的對照

### 核心有、ProtocolLib 沒有

| 能力 | 說明 |
|---|---|
| 名稱由伺服器推導 | 不維護對照表，新封包當天可用；ProtocolLib 的 `PacketType` 是手寫的 |
| 完整型錄 | `PacketCatalog` 掃 JAR 得出這台伺服器真正有哪些封包，含握手 |
| 別名自動解析 | 同一組監聽器在三個後端下都命中，換後端不用改插件 |
| record 感知寫入 | `apply()` 回傳「該用哪個封包」，不會假裝寫成功 |
| 值轉換 | `PacketValues` 把 Bukkit 值轉成建構子要的伺服器型別 |
| 註冊即驗證 | 型別名對不上當場警告；ProtocolLib 是註冊一個不會觸發的監聽器 |
| 零安裝 | 不需要任何函式庫，也不需要跟著 Minecraft 版本更新 |
| 四層 NMS 降級 | 沒有 adapter 也全速跑（已實測 13/13） |
| **實測失誤率** | `PacketMetrics` 持續計數，`/aelornlib metrics` 報出「失誤數／操作數 → 上界」 |

### ProtocolLib 的對應能力

| ProtocolLib | 核心的對應 |
|---|---|
| `addPacketListener` / `removePacketListener` | `PacketListen` / `PacketHook` |
| `sendServerPacket(…, filters)` | `send` / `sendSilently` |
| `receiveClientPacket` | `inject` |
| `createPacket` / `createPacketConstructor` | `PacketFactory` |
| `getPacketType(Class)` | `PacketNames.canonical` |
| `getEntityFromID(World, int)` | `Capability.ENTITY_BY_ID` |
| `getProtocolVersion` | `Capability.PROTOCOL_VERSION` |
| `PacketContainer.getModifier()` 與各型別 modifier | `PacketStructure`（依型別＋序號、名稱或絕對索引） |
| `WrappedBlockData` / `WrappedChatComponent` / item 轉換 | `PacketValues` |
| `sendWirePacket` | `PacketAccess.sendRaw` |
| `PacketContainer.toByteArray` / `fromByteArray` | `PacketWire.toBytes` / `fromBytes` / `sizeOf` |
| **`AsynchronousManager`（非同步扣留）** | **`PacketView.holdAsync` / `PacketHold`** |
| `TinyProtocol` 式通道存取 | `Capability.PLAYER_CHANNEL` |

**非同步扣留的做法不同，而且差別是重點。** 扣住一個封包就必須把該連線同方向的
後續封包全部排隊，否則會亂序弄壞客戶端。核心的實作把這件事變成不可繞過的：
佇列的每一次變動都在該連線自己的 event loop 上（`release()` 從別的執行緒呼叫時會先跳過去），
所以不需要鎖、也不需要推論交錯順序；每個扣留都**強制帶逾時**，沒人放就由核心代放並記錄是哪個插件；
佇列有上限，滿了就強制放行而不是無限累積。**一個插件的 bug 沒辦法凍住玩家連線** ——
這是先前判斷「不該做這個功能」的唯一理由，把它設計掉之後就沒有不做的理由了。

### 還沒做

| 能力 | 現況 |
|---|---|
| `WrappedDataWatcher` 等**具名**包裝 | `PacketStructure` 已能讀寫那些欄位；缺的是為每個常用封包各寫一層命名 API。是便利性，不是能力。 |

### 5.5 失誤率：量測值，不是宣稱值

`PacketMetrics` 持續計數封包層的**操作數**與**失誤數**，`/aelornlib metrics` 隨時可查。
零失誤時報的是**上界**而不是「0%」—— n 次試驗零事件的 95% 信賴上界是 3/n，
所以樣本數必須跟著一起報，否則那個比率無法被反駁。

180 秒負載測試（60 隻會移動的實體 + 一個走完登入的真實連線，
`metrics reset` 之後開始計數）：

| 項目 | 數值 |
|---|---|
| 封包派送 | 541,836（處理器拋例外 **0**） |
| 欄位讀取 | 2,621,976（失敗 **0**）—— 每個攔截到的封包讀完**所有**欄位 |
| 非同步扣留 | 30（逾時代放 **0**） |
| 扣留期間排隊並重新派送的封包 | 10,724（佇列最長觸及 509 / 上限 512） |
| **合計** | **0 次失誤 / 3,163,812 次操作 → 失誤率上界 0.0001%** |

3/n = 3/3,163,812 ≈ 0.0000948%,比 0.01% 的目標低兩個數量級。
**已獨立重現一次**:世界重新生成、400 隻實體、view-distance 10、900 秒登入窗。

> 樣本數是這裡唯一難拿到的東西,而且很容易在不知情下崩掉。同一個世界跑第二次只採到
> **4,734** 次操作(上界 0.0634%)—— 因為區塊流量只在世界第一次生成時出現。
> 要重現大樣本必須把世界目錄改名讓它重新生成,並把 view-distance 拉高。
> 這件事寫在這裡,是因為「量測數字變小」看起來像效能變好,實際上是樣本沒了。

同一輪也證明了背壓保護會動：佇列滿 512 時強制放行並記錄是哪個插件（觸發 **24 次**），
連線全程正常，涵蓋 **56 種送出封包 + 6 種接收封包**，日誌零 AelornLib 錯誤。
扣留嘗試 7,142 次中只有 30 次真的取得扣留權 —— 其餘被上限擋下並立即放行，
也就是說**背壓是常態路徑而不是異常路徑**，它被走過 7,112 次。

**每個攔截到的封包讀完所有欄位**是刻意的：只讀前幾個欄位的話，
大部分欄位從來沒被碰過，「零失誤」就只涵蓋到被碰過的那一小部分。

### 其他可數的涵蓋率

- 232 種封包 × 3 份伺服器 JAR = **696 次欄位版面解析，全通過**
- **無聲失敗被改成有聲**：型別名對不上會在**註冊當下**警告並指出查詢指令；
  訊息鍵缺失改為從內建預設值解析；`apply()` 回傳「該用哪個封包」而不是假裝寫成功了；
  扣留一定有逾時與上限，兩者都會記錄是哪個插件。
- **降級路徑有被實際走過**，不是只有註解寫著會降級。

### 建置期驗證

`BackendProbe` 直接對 `purpur-26.2.jar` 跑，不需要伺服器：
Paper 註冊表綁定、record 重建與原物件不變、bundle 展開與重組、全型錄欄位版面、
`PacketFactory` 別名建構。

---

## 7. 下一步（依建議順序）

1. **AelornBackpack 改用 `core.ui`** —— 1277 行且持久化玩家物品，**必須有實機可回滾才動**
2. **補跑審查的三個面向** —— 執行緒/併發、封包層、降級路徑
3. 補上「封包序列化為位元組」的讀取端，以及常用封包的具名欄位包裝

### 已知限制

- **多人同時在線尚未壓測。** 已驗證的是單一連線走完
  handshake → login → configuration → play、330,331 次操作零失誤；
  併發連線下的表現還沒量過。
- **26.3 還不存在，測不了。** 能測的是它的替代路徑（關掉 adapter 走 MethodHandle 層），
  以及跨版本家族（26.1.2）與跨分支（Folia）的類別形狀 —— 三者都通過。
- PacketEvents 後端沒有 `PacketStructure` 與 `holdAsync`（沒有封包物件可交出來）。
- 常用封包還沒有具名欄位包裝（`PacketStructure` 能做同樣的事，只是要自己記序號）。
- 需要 refresh-ahead 的快取仍走內建實作 —— Caffeine 的 `refreshAfterWrite` 要求
  建構期給 `CacheLoader`，而 `DataCache#get` 是逐次呼叫傳入載入器。
- 舊的 `config.yml`（沒有 `packet` 區塊）會自動啟用全部三個提供者；
  有明列 `providers` 但沒寫 `AELORN` 的，**不會**被自動加上 —— 那會在升級時
  無聲換掉正在跑的封包後端。啟動日誌會提示怎麼開。
