# RPGCore 0.14 裝備系統設定指南

RPGCore 原生裝備與 MMOItems 共用同一組能力、任務及職業需求。
原生裝備設定位於 `equipment.yml`，目前 schema 為 2。

## 穿戴需求

```yaml
requirements:
  classes: [ranger]
  skills:
    dexterity: 20
    agility: 10
  quests: [road_patrol]
```

未達需求時，物品仍可放入背包，但近戰、右鍵與射擊會被阻止；提示會走
RPGCore HUD 通知管線。管理員可使用 `rpgcore.equipment.bypass` 略過限制。

## 大型特性

```yaml
major-identification:
  id: tailwind
  display-name: '順風連矢'
  description:
    - '遠距離命中會短暫提高下一箭的投射速度。'
```

大型特性是供能力樹、戰鬥監聽器或任務腳本辨識的固定 ID，不應當成隨機數值。

## 詞條

`base-stats` 每次都會出現，`affixes` 依 `affix-count` 抽取。

```yaml
base-stats:
  attack: { minimum: 8, maximum: 13 }
  range: { minimum: 2, maximum: 5 }
affix-count: { minimum: 1, maximum: 3 }
affixes:
  projectile_damage: { minimum: 3, maximum: 9 }
  critical_strike_power: { minimum: 5, maximum: 18 }
```

可使用主攻擊、法術、百分比傷害、攻速、暴擊、穿透、五元素、生命、
防禦、資源、移動、掉寶與五項主要能力詞條。MMOItems 物品由服務 API
傳入後會映射到相同數值，未達需求的物品不會提供任何角色加成。

## 七個品質

`common` 凡品、`uncommon` 優良、`rare` 稀有、`epic` 史詩、
`legendary` 傳說、`vast` 浩瀚、`mythic` 神話。

每個掉落來源都能用 `rarity-weights` 單獨調整機率；鑑定價格倍率位於
`config.yml > identification.cost.rarity-multipliers`。
