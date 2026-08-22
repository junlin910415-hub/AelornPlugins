# AelornStore — 儲值金 / 商店 / VIP 系統

Folia (26.2) 插件 + Node.js 金流後端。以真實金流為前提設計:整數記帳、訂單狀態機、
冪等發貨、離線補發、可回收退款、雙寫稽核。

```
plugin-sources/AelornStore/
├── src/main/java/tw/linsy/aelornstore/   # 插件(44 個檔)
├── src/main/resources/                   # config.yml / messages.yml / shop.yml / vip.yml
└── web/                                  # Node.js 付款頁與 callback 接收端
```

---

## 1. 架構:為什麼要拆成兩半

```
   遊戲內                        公網 (VPS)                   金流商
┌──────────────┐            ┌──────────────────┐         ┌──────────┐
│ /store topup │            │                  │         │          │
│      ↓       │            │  AelornStore web │         │  綠界 /  │
│  建立訂單 ───┼──── DB ───▶│                  │◀───────▶│  藍新    │
│  status=     │            │  · 付款頁        │ callback│          │
│  PENDING     │            │  · 驗簽          │         │          │
│      ↑       │            │  · status=PAID   │         └──────────┘
│  發貨輪詢 ◀──┼──── DB ────┤                  │
│  status=     │            └──────────────────┘
│  DELIVERED   │
└──────────────┘
```

**Minecraft 伺服器不對外開任何埠。** callback 進 Web 後端,插件只讀資料庫。
這一刀切下去,以下全部成立:

| 風險 | 為什麼被擋掉 |
|---|---|
| 玩家偽造付款通知 | 通知只進 Web 後端,且必須通過金流商簽章驗證 |
| MC 伺服器被打 | 它沒有對外服務,金鑰也不在它身上 |
| 金流商重送 callback | `UPDATE ... WHERE status IN ('CREATED','PENDING')` — 只有第一次會改到列 |
| 連點兩次購買 | 扣款、庫存、限購在同一個 transaction 內重查 |
| 發貨到一半當機 | 訂單卡在 `DELIVERING`,回收掃描 5 分鐘後放回佇列 |
| 玩家離線時付款 | 排入佇列,上線自動補發(且不消耗重試次數) |
| 信用卡 chargeback | `/astore order refund` 回收點數與 VIP;餘額不足會歸零並記 `REFUND_SHORTFALL` |

## 2. 記帳原則

**所有金額都是整數。** 全系統沒有任何一個 `double` 參與金額運算。

- 真實貨幣:`amount_minor`,單位「分」(100 = NT$1)
- 儲值金:整數「點」
- 綠界/藍新的金額欄位只收整數「元」,所以 `economy.money.require-whole-units: true`
  會在建單時擋掉非 100 倍數的金額
- 錢包每一次變動都寫一列 `wallet_tx`(含變動後餘額),餘額永遠能被帳本解釋

## 3. 訂單狀態機

```
CREATED ──▶ PENDING ──▶ PAID ──▶ DELIVERING ──▶ DELIVERED ──▶ REFUNDED
   │           │                      │
   └───────────┴──▶ CANCELLED         └──▶ NEEDS_ATTENTION ──▶(redeliver)
               └──▶ EXPIRED
               └──▶ FAILED
```

每一次推進都是條件式 `UPDATE ... WHERE status = ?`。只有看到影響列數 1 的那一方贏,
所以重送的 callback、雙擊的按鈕、兩台同時輪詢的伺服器,全部收斂成一次發貨。

## 4. 目前狀態

| 元件 | 狀態 |
|---|---|
| 儲值金錢包 + 帳本 | ✅ 完成 |
| 商店 GUI(分類/分頁/確認/折扣) | ✅ 完成 |
| 庫存、每人限購、每日限購 | ✅ 完成 |
| VIP 等級、升級折算、到期掃描、到期提醒 | ✅ 完成 |
| 訂單狀態機 + 冪等發貨 + 離線佇列 | ✅ 完成 |
| 退款回收(點數 / VIP / 商品權限 / 庫存) | ✅ 完成 |
| 稽核(資料庫 + 純文字檔雙寫) | ✅ 完成 |
| 人工核銷管道(匯款/超商) | ✅ 完成,不需任何金流商 |
| Web 付款頁 / 訂單查詢 / callback 路由 | ✅ 完成 |
| 服務條款與退款政策頁(`/terms`) | ✅ 範本完成,**待填營業人資料與法律確認** |
| 「已發放商品不退換」告知(遊戲內 + 付款頁 + 條款) | ✅ 完成 |
| **綠界 ECPay 適配層** | ⬜ **介面已定,實作待補** — `web/src/providers/ecpay.js` |
| **藍新 NewebPay 適配層** | ⬜ **介面已定,實作待補** — `web/src/providers/newebpay.js` |

未實作的管道會自動被標為不可用:玩家不會拿到一個開不了的付款連結,
付款頁也不會顯示那個按鈕。**現在就可以只用人工核銷正式營運。**

## 5. 安裝

### 插件

```bash
powershell -ExecutionPolicy Bypass -File plugin-sources/build-all.ps1 -Only AelornStore
```

產物 `AelornStore/build/libs/AelornStore-1.0.0-folia-26.2.jar` 丟進 `plugins/`,重啟。

插件本身**沒有**與任何 JDBC 驅動編譯期相依(一律走 `java.sql` + `Class.forName`),
所以**建置階段完全離線**。

驅動由 Paper 的 library loader 在啟動時提供(`plugin.yml` 的 `libraries:`)。
注意:**首次啟動需要網路** —— 即使 JAR 已在 `libraries/` 快取,解析器仍會去 Maven Central
抓 `.pom` metadata(以及 `mysql-connector-j` 的傳遞相依 `protobuf-java`)。
POM 下載後一併快取,第二次之後的啟動才真正離線。實測:

```
首次  [SpigotLibraryLoader] Downloading .../sqlite-jdbc-3.49.1.0.pom   ← 需要網路
第二次 [SpigotLibraryLoader] [AelornStore] Loaded library ...           ← 無下載，離線
```

要完全離線佈署,先在有網路的機器啟動一次,再把整個 `libraries/` 帶過去。

### 資料庫選擇

| 情境 | 設定 |
|---|---|
| 只跑人工核銷,或 Web 後端與 MC 同一台主機 | `storage.type: SQLITE`(已開 WAL,兩個行程可共用同一檔案) |
| Web 後端與 MC 不同主機 | `storage.type: MYSQL` |

Schema 由插件建立。Web 後端**不會**動 schema —— 能各自遷移的兩半遲早會不一致。

### Web 後端

見 [`web/README.md`](web/README.md)。

## 6. 指令

### 玩家 `/store`(別名 `/shop` `/商店` `/儲值`)

| 指令 | 說明 |
|---|---|
| `/store` | 開啟商店 |
| `/store balance` | 查儲值金餘額 |
| `/store topup` | 開啟儲值選單 |
| `/store topup <金額> [管道]` | 直接建立訂單(金額以「元」輸入) |
| `/store orders` | 我的最近訂單 |
| `/store claim` | 手動領取待發貨內容 |
| `/store vip` | 我的 VIP 狀態 |

### 管理 `/aelornstore`(別名 `/astore`)

| 指令 | 說明 |
|---|---|
| `reload` | 重載 config / shop / vip / messages(storage 變更需重啟) |
| `status` | 資料庫、商品數、金流管道、待付款/待發貨/待人工筆數 |
| `credit <玩家> <give\|take\|set> <數量> [原因]` | 調整儲值金 |
| `order info <訂單號>` | 訂單明細 |
| `order approve <訂單號>` | **人工核銷** — 匯款到帳後用這個 |
| `order cancel <訂單號>` | 取消未付款訂單 |
| `order refund <訂單號>` | 退款並回收發放內容 |
| `redeliver <訂單號>` | 把卡住的訂單放回發貨佇列 |
| `vip set <玩家> <等級> [天數]` | 手動給 VIP(天數 0 = 永久) |
| `vip clear <玩家>` | 清除 VIP |
| `audit <玩家> [筆數]` | 稽核紀錄 |

## 7. 設定

程式碼零寫死。所有數值、文案、商品、VIP 等級、GUI 版面都在 YAML:

| 檔案 | 內容 |
|---|---|
| `config.yml` | 資料庫、金額上下限、風控、輪詢間隔、冷卻、稽核 |
| `shop.yml` | GUI 版面(rows / filler / 每個按鈕的槽位)、儲值方案、分類、商品 |
| `vip.yml` | VIP 等級、權重、折算基準、授予/到期動作 |
| `messages.yml` | 全部玩家可見文字。留白 = 不顯示這則訊息 |

任何一項解析失敗只會被記錄並跳過,不會讓整份目錄載入失敗。

### 發放動作語法

`shop.yml` 的 `actions:` / `revoke-actions:` 與 `vip.yml` 的 `on-grant:` / `on-expire:`:

```yaml
actions:
  - "console: lp user {player} parent add vip_gold"   # 主控台執行
  - "player: warp spawn"                              # 玩家身分執行
  - "credit: 500"                                     # 給儲值金
  - "vault: 1000"                                     # 給遊戲幣
  - "vip: tier=gold days=30"                          # 授予/延長 VIP(days=0 為永久)
  - "item: DIAMOND 3"                                 # 原版物品
  - "message: &a感謝支持!"                            # 私訊
  - "broadcast: &6{player} 成為了 VIP"                # 全服公告
  - "sound: entity.player.levelup 1.0 1.2"            # 音效
```

可用佔位符:`{player}` `{uuid}` `{order}` `{product}` `{quantity}` `{amount}` `{credit}`

AelornItems / Nexo / RPGCore 物品請走 `console:` 呼叫各自的 give 指令,
插件不與它們硬相依。

> **`revoke-actions` 要自己寫。** `credit:` 與 `vip:` 退款時會自動反轉,
> 但 `console:` 做了什麼、系統無從得知。有 `console:` 的商品就要補對應的
> `revoke-actions`,否則退款只會扣點數、不會收回權限。

## 8. 風險控管(台灣實務)

`config.yml` 的 `topup:` 區塊,預設值已經偏保守:

| 設定 | 預設 | 為什麼 |
|---|---|---|
| `daily-limit-minor` | 1000000(NT$10,000) | MC 玩家年齡層低,**未成年盜刷後家長申訴 chargeback 是最實際的財務風險**。單日上限是最有效的一道 |
| `max-pending-orders` | 3 | 擋洗單 |
| `account-age-hours` | 24 | 盜刷者慣用「註冊即刷、刷完即棄」;要求帳號存在滿一天,成本就高到不划算。代價是新玩家當天不能課金,辦活動想放寬就改這裡 |
| `min/max-amount-minor` | NT$30 / NT$20,000 | 單筆上限直接壓低單次損失 |
| `broadcast-above-minor` | 50000 | 高額訂單公告,也等於讓社群幫忙盯 |

另外:**優先推超商代碼 / ATM 虛擬帳號**(無 chargeback),信用卡當補充。

## 9. 合規提醒

這些不是程式問題,但會決定這套系統能不能上線:

- **商家資格** — 綠界/藍新都有個人賣家方案(免公司登記),但費率較高、有額度上限,
  且「虛擬商品/遊戲點數」屬高風險類別會另外審核,信用卡對個人戶尤其嚴。
  長期營運建議辦商業登記。
- **不需要電支執照** — 收自己的錢不算電子支付業務(代收代付「別人」的款項才需要)。
- **《線上遊戲定型化契約應記載及不得記載事項》**(數位發展部主管)適用於販售遊戲點數
  /虛擬寶物:要有退費機制、停止營運須提前公告並處理未使用點數。
  後端的 `/terms` 就是放這份的,內容在 `web/content/terms.md`。
- **「已發放商品概不退換」是有效的,「一律不退」不是。** 條款第 8.3 條保留的法定除外
  情形(系統錯誤、業者過失、未成年人未經同意消費、停止營運)不能刪 —— 那些依法不能
  用契約排除,而且列在**不得記載事項**裡。寫成一律不退,整條無效,保護作用歸零,
  還可能被命限期改正並處罰鍰。
- **稅** — 小規模營業人營業稅起徵點目前為銷售貨物月營業額 10 萬 / 勞務 5 萬,
  超過要辦稅籍登記。
- **Mojang 商用準則** — 付費內容不得影響遊戲平衡(pay-to-win)。`shop.yml` 的範例
  刻意全部走外觀與便利性。RPGCore 裝備/屬性類商品是踩線的。

> 以上為實務整理,法規與費率會變動,實際以主管機關與金流商當下公告為準。

## 10. 待辦

1. 決定金流商 → 完成 `web/src/providers/<商>.js` 三個函式 → 在 `config.json` 與
   `config.yml` 兩邊把該管道 `enabled` 打開。
2. 填 `config.json` 的 `site.operator`(營業人名稱、統編、地址、管轄法院…),
   核對 `web/content/terms.md` 的條款內容並**刪除開頭的「發布前必讀」草稿說明**。
   啟動時若還有未填欄位或草稿說明,主控台會警告。
3. 把 `shop.yml` 的範例商品換成實際商品,並為每個帶 `console:` 的商品補
   `revoke-actions`。
4. 上線前用測試環境跑一次完整流程:建單 → 付款 → callback → 發貨 → 退款。
