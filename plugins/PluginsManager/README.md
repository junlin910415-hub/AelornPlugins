# PluginsManager

艾洛恩伺服器的插件熱管理：啟用／停用／重載／載入／卸載，加上安全護欄、稽核紀錄與 jar 版本保存。

支援 **LightingLuminol 26.2**（Folia 系，分區執行緒）與 **Purpur 26.2**（Paper 系，單執行緒），同一份 jar。

---

## 為什麼有 2.0.0

1.x 是反編譯產物，且 `unload` 是壞的。它反射 `SimplePluginManager` 的 `plugins` / `lookupNames`，但在現代 Paper 系核心上那兩個集合已經是死的——實測 `SimplePluginManager` 有 34 處委派給 `paperPluginManager`，只剩 1 處還碰舊欄位。真正的註冊表在 `PaperPluginInstanceManager`。

結果是：**卸載回報成功，伺服器卻仍認為插件在載入中，而 classloader 已經被關掉。**

2.0.0 一併修掉的其他缺口：

| 缺口 | 後果 |
|---|---|
| classloader 關了但沒從 Paper 的 group storage 反註冊 | 其他插件仍能解析到已卸載插件的類別；Windows 上 jar 還被鎖住 |
| 指令樹從不重送 | 客戶端持續補全已不存在的指令，直到重連 |
| 權限從不移除 | 重新載入時每個節點噴一次重複警告 |
| 相依判定只看直接相依 | 停用相依鏈上第二層看起來安全，實際會弄壞東西 |
| 相依判定用 `PluginDescriptionFile` | 對 `paper-plugin.yml` 插件全盲 |
| 反射取 `knownCommands` / `getScheduler` / `getFile` | 這三個都有公開 API |

---

## 分層

```
PluginsManagerPlugin     啟動接線,不含任何行為
platform/                無核心 API 邊界 + AelornLib 回退（排程、渲染、平台探測）
nms/                     版本無關門面 + impl/v26_2 唯一碰伺服器內部的檔案
config/                  YAML → immutable 快照,執行期不再讀 YAML
model/                   領域資料,record 為主
service/                 行為,一個 service 一個職責
command/                 分派:權限 → 執行緒 → service
audit/                   稽核 sink:檔案（預設）或批次 SQL
```

依 `plugin-sources/CONVENTIONS.md`。

---

## 兩個刻意的設計決定

**AelornLib 是 `softdepend`，不是 `depend`。** 核心在找不到對應 NMS adapter 時會 fail closed，插件管理器不該跟著陪葬——管理員最需要停用或回滾插件的時刻，正好是升級剛把核心弄壞的時候。核心在就委派排程與文字格式，不在就用內建回退（`platform/ApiSched`、`platform/AutoRenderer`）。

**NMS adapter 只有一份，兩個 fork 共用。** LightingLuminol 與 Purpur 共享 Mojang-mapped 內部、無版本後綴的 CraftBukkit、Paper 的插件管理器與 Moonrise 的 `TickThread`；唯一的差異是「世界有沒有切成區域執行緒」，那是執行期問題（`platform/PlatformProfile` 用 `RegionizedServer` 類別存在與否判定），不是編譯期問題。建置時 adapter 會**對兩個核心各編一次**，其中一次只當閘門——簽章一旦分家就當場失敗。

NMS 只做四件 API 做不到的事：Paper 真正的插件註冊表、Brigadier 指令樹 resync、伺服器自己的遞移相依答案、tick 執行緒判定。其餘（指令表、classloader 反註冊、權限、服務、監聽器）全走公開 API。

---

## 建置

完全離線，不連網。

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

`build.ps1` 會從腳本位置往上找出含 `libraries\` 的伺服器目錄；放在別處就用 `-ServerRoot` 指定：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 -ServerRoot "D:\你的伺服器"
```

**這個 repo 只含原始碼。** 編譯需要伺服器樹提供以下第三方二進位檔（都無法進版控）：

| 需要的東西 | 用途 |
|---|---|
| `<ServerRoot>\libraries\` | folia-api、adventure、guava、annotations、bungeecord-chat、HikariCP、slf4j |
| `<ServerRoot>\versions\26.2\folia-26.2.jar` | NMS adapter 的編譯目標 |
| `<ServerRoot>\versions\26.2\purpur-26.2.jar` | 可選：第二核心閘門，簽章分家會當場失敗 |
| `<ServerRoot>\plugins\AelornLib-*.jar` | 核心委派層的編譯期相依（執行期才是選用的）|

編譯規則：

- `--release 25`（class 69）——伺服器 `version.json` 標 `java_version: 25`，這是上限
- classpath 不含伺服器核心，只有 `nms/impl/v26_2/` 例外；型別洩漏會當場編不過
- 同一份 adapter 對 folia 與 purpur 各編一次，第二次只當閘門

艾洛恩本身的完整建置走上一層的 `build-all.ps1`（依序建整個 `plugin-sources`）；`build.ps1` 是給「只 clone 了這個 repo」的情況用的，編譯規則相同。

---

## 設定

五個檔案，全部可 `/zpm config reload` 熱重載：

| 檔案 | 內容 |
|---|---|
| `config.yml` | 護欄、監看器、稽核儲存 |
| `messages.yml` | **所有**玩家可見文字（133 個鍵，程式碼裡一句話都沒有）|
| `groups.yml` | 插件群組與批次操作 |
| `commands.yml` | 指令索引與反註冊 |
| `version-control.yml` | jar 版本保存 |

稽核預設寫 `audit.log`（JSON 每行一筆，每筆立即 flush）。可改成批次 SQL，但檔案仍是預設——稽核紀錄的可用性不該取決於資料庫活著，你需要查稽核的時候往往正是出事的時候。

---

## 已知限制

**RCON 與自動化收不到指令輸出。** 每個子指令都會依 `Subcommand.where()` 排到全域區域或非同步執行緒，回覆是在那之後才送出的；RCON 只收集指令**同步執行期間**寫給 sender 的訊息，所以回應永遠是空的。指令本身有正常執行（稽核紀錄與設定檔變更都看得到），只是看不到回覆。

這不是這次改版造成的——1.x 同樣把每個操作都 `runGlobal(...)`，行為一致。要修的話得知道「目前是否正在全域區域執行緒上」才能安全地就地執行，而 Moonrise 的 `TickThread.isTickThread()` 在**任何**區域執行緒上都回傳 true，分不出全域與否，所以沒有貿然做。玩家與伺服器主控台不受影響。

---

## 狀態

編譯通過，訊息鍵零缺零冗，class 版本 69（Java 25），NMS adapter 對 folia-26.2 與 purpur-26.2 皆編譯通過。

**實機驗證通過**（LightingLuminol 26.2 / AelornLib 1.0.0 / 31 支插件、0 啟用錯誤）。完整的熱重載往返：

```
/zpm unload AelornQuestBridge --confirm
  → SUCCESS：停用; 取消排程=2; 監聽器; 服務; 訊息通道; 指令=4; 權限=1;
             插件註冊表; 類別載入器=Paper 類別載入器（已反註冊）; 指令樹

/zpm load AelornQuestBridge-3.1.0-folia-26.2.jar --confirm
  → SUCCESS：載入 36 個任務定義，完全正常運作
```

`load` 能成功正是反註冊確實生效的證據 —— 若插件還留在伺服器註冊表裡，這裡會回報「已經載入」。

換核心的相容性可以先用 `plugin-sources/tools/check-core-compat.py` 檢查：它直接解析 class 檔常數池，比對每個 `(擁有者, 名稱, 描述子)` 三元組，抓得到「類別還在但方法簽章換了」這種 `NoSuchMethodError`。
