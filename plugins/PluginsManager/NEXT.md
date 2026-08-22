# 下一階段：GUI、指令重寫、三支 API

這份是交接。目的是讓下一次接手不必重建脈絡 —— 已經查證過的事實寫在這裡，還沒決定的事列成問題。

---

## 一、參考插件學到什麼

**PluginManagerPlus 2.9.6**（`net.pinger.management`）的三個結構性決定：

| 它怎麼做 | 值不值得抄 |
|---|---|
| **每個畫面一個 Provider**：`PluginsProvider`（清單）→ `PluginProvider`（單一插件詳情）→ `PluginGroupProvider`（群組）→ `CommandsProvider`（指令索引）→ `ExploreProvider` | ✅ 值得。畫面之間的狀態轉移變成物件之間的轉移，不是一個巨大的 click switch |
| **Drink 註解式指令框架**（shaded 的 `com.jonahseguin.drink`）：`@Command` / `@Flag` / `@OptArg` / `@Require` / `@Sender`，`plugin.yml` 完全沒有 `commands:` 與 `permissions:` 區塊，全在執行期註冊 | ⚠️ 部分。我們現在的 `Subcommand` 表已經是宣告式的，且不必 shade 第三方框架。**但它的 `@Require` 把權限綁在方法上，比我們把權限寫在表裡更難漏掉** —— 這點值得吸收 |
| **`PluginExpansion`**：把插件資訊做成 PlaceholderAPI 佔位符 | ✅ 值得，成本低 |

注意它 `plugin.yml` 不宣告權限節點的代價：LuckPerms 之類的權限外掛看不到有哪些節點可以授權。我們維持在 `plugin.yml` 宣告。

---

## 二、GUI 設計（建在 AelornLib 的 Menu 框架上）

AelornLib 1.0.0 的 `core.ui` 已經提供所需的一切，**不要再造一套**：

```java
Menu.rows(plugin, 6, title)
    .border(filler)
    .content(items, Menu.slotRange(0, 44))   // 分頁自動由兩個長度推出
    .set(49, MenuItem.button(icon, click -> click.close()))
    .storage(slots...)                        // 明確標示哪些槽是真的儲存
    .onClose(player -> ...)
    .open(player);
```

- 點擊預設**取消**，`click.allowVanilla()` 才放行 —— 這是背包與物品複製器的差別
- `click` 跑在玩家自己的區域執行緒上，可以直接碰世界，不需要 hop
- 標題可即時更換（實機已驗證：`選單標題可即時更換（PacketEvents + containerId）`），所以分頁不閃爍
- `Menus.canRetitle()` 決定要不要把頁碼放進標題

**建議的畫面**（對應 PluginManagerPlus 的 provider 切法）：

| 畫面 | 內容 | 進入點 |
|---|---|---|
| 總覽 | 插件清單、分頁、狀態色（啟用／停用／受保護）、搜尋 | `/zpm gui` |
| 單一插件 | 版本、主類別、API、相依／被相依、jar、可用動作（啟用／停用／重載／卸載／保存版本） | 點總覽的一格 |
| 群組 | `groups.yml` 的群組與批次動作 | 總覽的按鈕 |
| 版本 | 該插件的保存版本、還原 | 單一插件的按鈕 |
| 指令索引 | 指令 → 擁有者、衝突標紅、反註冊 | 總覽的按鈕 |

**每個破壞性動作在 GUI 裡仍然要走同一組 `OperationGuards`** —— 不要為了 GUI 另開一條繞過確認與保護的路徑。GUI 的「確認」用第二層選單取代 `--confirm`。

---

## 三、三支 API 各自該用在哪

| API | 用途 | 怎麼取用 |
|---|---|---|
| **PacketEvents / ProtocolLib** | 選單標題即時更換 | **不要直接呼叫**。走 `AelornLib` 的 `Packets` / `Menus.surface()`，核心已經抽象掉兩者差異（實測分歧：兩套函式庫僅 85/168 送出、31/75 接收同名）|
| **PlaceholderAPI** | `%pluginsmanager_total%`、`%pluginsmanager_enabled%`、`%pluginsmanager_disabled%`、`%pluginsmanager_protected%`、`%pluginsmanager_state_<插件>%` | 註冊一個 `PlaceholderExpansion`，softdepend |
| **AelornLib** | 排程、文字、NMS、資料層 | 已接。`core.data().open(...)` 拿 Hikari 連線池、`core.data().cache(...)` 拿 Caffeine |

**資料庫**：`audit.storage: sql` 已經實作（批次 INSERT、方言偵測、降級重試）。下一步是把它改走 `core.data().open(...)`，就能刪掉 `audit/ConnectionSource*` 那 ~300 行與 HikariCP 編譯相依 —— 核心的 `Database` 已經有 `batch(...)`、健康檢查與統計。

---

## 四、需要你決定的安全姿態

授權宣告與檢查已對齊，這四項不是 bug，是**預設值與權限切分的取捨**，我沒有自作主張：

1. **`zpm.manage` 把 `unload` 和 `enable` 綁在一起。** 卸載會動伺服器內部註冊表，啟用只是一次 API 呼叫。建議拆出 `zpm.unload`。
2. **`zpm.command` 可以反註冊任何指令**，包括 `/stop`、`/op` 與其他插件的。節點名稱聽起來無害，實際權力很大。
3. **`zpm.version` 的 `restore` 會覆寫插件 jar** → 下次載入即程式碼執行。名稱聽起來像「版本紀錄」。
4. **`auto-load-new-jars: true` 是預設值。** 任何能寫入 `plugins/` 的人都能讓程式碼被自動載入並啟用。

拆權限節點是安全的：舊節點在 `plugin.yml` 宣告為新節點的父節點，既有授權不會失效，管理員可以再收緊（見 CONVENTIONS.md §7）。

---

## 五、目前狀態

- 實機：LightingLuminol 26.2 / AelornLib 1.0.0 / PluginsManager 2.0.0，31 插件 0 錯誤
- 熱重載 unload → load 往返實測通過
- 授權：7 節點宣告 = 7 節點檢查，19 個子指令全有對應，tab 補全依權限過濾
- 已修：restore 路徑containment、拒絕操作進稽核
- **RCON 收不到指令輸出**（回覆是排程後才送的）；玩家與主控台不受影響
