# GriefPreventionAddon

**Lycohinya 領地擴充與管理插件。** 專為 GriefPrevention 設計的擴充系統，提供領地 TNT 爆炸控制、領地 PVP / 自然生物生成管理、一鍵安全傳送、以及符合伺服器設計規範的花域設定 GUI。

> Lycohinya 伺服器自製插件，完全相容 **Paper / Folia / Lecithin 26.2** 多執行緒與 regionised 排程架構。硬相依為 **GriefPrevention** 與 **LycoLib**。

---

## 主要功能

| 功能 | 說明 | 指令 |
|---|---|---|
| **TNT 爆炸控制** | 地主自主決定領地是否允許 TNT 破壞方塊，實時過濾爆炸清單，保護禁止領地 | `/ctnt [on\|off\|status]` |
| **花域設定選單** | 4 列帶狀 Chest GUI，視覺化管理領地狀態、一鍵傳送、TNT、PvP 與 5 類生物 | `/csettings [領地ID]` |
| **一鍵領地傳送** | 計算領地幾何中心安全地面座標，透過 Folia 異步排程安全傳送 | `/claimtp [領地ID]` (`/ctp`) |
| **領地 PVP 開關** | 地主自由開關領地內 PVP 戰鬥狀態，附帶全域提示廣播 | `/pvp` |
| **自然生物生成管理** | 分類開關自然生怪（一般敵對 / 掠奪者 / 夜魅 / 史萊姆 / 蝙蝠），不影響生怪磚與繁殖 | `/cmob [分類]` |
| **領地資訊查詢** | 顯示當前領地尺寸、主人與狀態，並提供可點擊的傳送與設定捷徑 | `/claiminfo` |

---

## 需求

| 項目 | 規格 |
|---|---|
| 核心平台 | **Paper / Folia / Lecithin 26.2** |
| Java 版本 | **Java 25** |
| 硬相依插件 | **GriefPrevention**, **LycoLib** |

---

## 指令與權限

### 玩家指令

| 指令 | 別名 | 權限 | 說明 |
|---|---|---|---|
| `/ctnt [on\|off\|status]` | `/claimtnt` | 領地主人或管理員 | 查詢或切換當前花域 TNT 爆炸破壞 |
| `/csettings [領地ID]` | `/claimsettings` | 領地主人、信任成員或管理員 | 開啟花域設定 GUI 選單 |
| `/claimtp [領地ID]` | `/ctp`, `/claimteleport` | `griefpreventionaddon.tp` (預設 true) | 一鍵傳送至花域中心安全位置 |
| `/pvp` | `/pvpinclaim` | 領地主人或管理員 | 切換當前花域 PVP 對戰狀態 |
| `/cmob [分類]` | `/claimmobs` | 領地主人或管理員 | 切換或開啟生物自然生成設定選單 |
| `/claiminfo` | 無 | 無 (所有人可用) | 查詢當前站立的花域資訊與快速操作捷徑 |

### 管理員權限

* `griefpreventionaddon.admin`: 管理員全權限（可操作任意玩家領地與所有子指令）。
* `griefpreventionaddon.tnt.others`: 可修改他人領地的 TNT 設定。
* `griefpreventionaddon.pvp.others`: 可修改他人領地的 PVP 設定。
* `griefpreventionaddon.mobs.others`: 可修改他人領地的生物生成設定。
* `griefpreventionaddon.settings.others`: 可開啟並修改他人領地的設定選單。
* `griefpreventionaddon.tp.others`: 可傳送至任意玩家的領地中心。

---

## 資料庫與儲存

* 使用 WAL 模式 SQLite 資料庫 (`plugins/GriefPreventionAddon/griefPreventionAddon.db`)。
* 具備記憶體快取層，確保高頻事件（如爆炸過濾、生怪監聽）零延遲 $O(1)$ 查詢。
* 首次啟動自動自 `LycoServerTweaks` 遷移既有領地 PVP 與生物設定。

---

## 建置與測試

```bash
./gradlew build        # 編譯與打包 shadowJar
./gradlew test         # 執行單元測試 (DB CRUD、TNT 邊界過濾、夜魅錨點算法、傳送座標)
```

---

## 授權

TinyYana · [tinyyana.com](https://tinyyana.com)
