# RPGCore HUD V2 整合指南

本目錄是 RPGCore 0.14.0 內建 HUD 的動態化升級模組（全部使用你自有的
`rpgcore_hud` 位圖字型與碼位，不引入任何外部素材）。

| 模組 | 內容 |
|---|---|
| `hud/HudMotionState.java` | 每玩家動畫狀態機：緩動條、受擊閃爍、移動狀態驅動幀速 |
| `hud/InternalHudComposerV2.java` | 組版器 V2：右上角座標、閃爍著色、吃緩動輸出 |

## 接線（InternalBossBarHudRenderer）

0.14.0 的 `CombatHudSnapshot` 已內含 `movementMode`（STAMINA/AIR）與座標欄位，
資料端不需改。渲染端改動：

```java
// 每玩家一份動畫狀態（與 BossBar 一起以 UUID 管理，quit 時移除）
private final Map<UUID, HudMotionState> motions = new ConcurrentHashMap<>();

// 更新 tick（玩家 EntityScheduler 內）：
HudMotionState motion = motions.computeIfAbsent(player.getUniqueId(), id -> new HudMotionState());
boolean moving = /* 與上次記錄位置的水平距離平方 > 0.0004 */;
HudMotionState.Frame frame = motion.tick(snapshot, player.isSprinting(), moving);
bossBar.name(composerV2.compose(snapshot, frame));
```

- 建構子：`new InternalHudComposerV2(maxNotificationCodePoints, hudSettings.coordinatesEnabled(), hudSettings.coordinatesOffsetX())`
- 角色切換 / `/rpg hud on` 時呼叫 `motion.reset()`，避免殘影動畫。
- 更新頻率建議 2 tick 一次（10fps 動畫已足夠平滑，BossBar 封包量減半）。

## 新增設定（config.yml → hud 區塊）

```yaml
hud:
  coordinates-enabled: true
  # 以畫面中心為原點的向右偏移像素；GUI Scale 2 時 +320 約在右上角。
  coordinates-offset-x: 320
```

注意：BossBar HUD 以畫面中心為原點，「右上角」的實際落點與客戶端 GUI Scale
相關，這是 BossBar 傳輸的固有限制；偏移量做成設定值即為此因。

## 效果對照（本次涵蓋的需求）

| 需求 | 狀態 |
|---|---|
| 血量/魔力顯示更清晰 | 緩動 + 受擊閃爍著色（`HEALTH_FLASH`），數值變化可追蹤 |
| 中間那條動畫化 | 幀速隨 靜止/走路/游泳/跑步 改變（0.15–0.62 幀/tick） |
| 走路/跑步耐力、入水改氧氣 | 0.14.0 已有 STAMINA/AIR 語意，V2 保留並以 SWIM 幀速強化 |
| 右上角座標 | `coordinates-enabled` + 偏移設定 |

## 素材升級（需要新畫的圖，非程式）

要達到參考包（mcmodels 商品）那種質感，需在 `rpgcore-hud-pack` 內重繪：
狀態框 3 幀 → 建議 6–8 幀、血/魔條加高光層、耐力/氧氣圖示各 2 幀呼吸動畫。
碼位建議接續現有表（0xCE082 起），`InternalHudPackService` 的打包流程不變。
mcmodels 的三個 HUD 是付費商品：**購買後**可把其貼圖放入自家包內使用，
但不能由我重製或抄襲其圖樣；我能做的是在你提供已購素材後接上碼位與版面。

## MythicHUD 是否需要：不需要

- RPGCore 已有 Action Bar 隔離架構 + 內建 BossBar 渲染器（Folia-safe），
  外加 BetterHud 作為可選渲染器（有界重試、晚掛接）。
- 再加 MythicHUD 會是第三套 HUD 管線，與 Action Bar 隔離及 Oraxen
  單一資源包路線衝突（`CROSS-PLUGIN-INTEGRATION.md` 的部署規則）。
- 結論：投資在內建渲染器素材升級 + BetterHud 版面即可，不裝 MythicHUD。
