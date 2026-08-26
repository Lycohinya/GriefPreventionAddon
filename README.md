# GriefPreventionAddon

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Minecraft Paper](https://img.shields.io/badge/Minecraft-Paper%20%2F%20Folia%2026.2-brightgreen.svg)](https://papermc.io/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)](https://kotlinlang.org/)

**A modern, feature-rich companion addon for [GriefPrevention](https://github.com/TechFortress/GriefPrevention).**  
Provides per-claim TNT protection, guest `/sethome` permission management, PvP toggles, natural mob spawning filters, custom claim aliases, fast teleportation, custom landing points, interactive GUI menus, and a global administrative panel.

Supports **Paper**, **Folia**, and **Lecithin 26.2** with asynchronous, thread-safe region scheduling, zero hard dependencies on external libraries, and full multi-language (i18n) localization with auto-detection of player client locales.

---

## 🌟 Features / 主要功能

| Feature | Description (English) | 說明 (繁體中文) | Command |
|---|---|---|---|
| **TNT Explosion Control** | Claim owners can allow or block explosion block damage inside their claim. Precise edge-filtering protects boundary blocks. | 地主自主決定領地內是否允許 TNT 爆炸破壞方塊，邊界過濾演算法精準保護領地方塊。 | `/ctnt [on\|off\|status]` |
| **Guest `/sethome` Control** | Claim owners decide whether untrusted visitors can use `/sethome` in their claim, preventing unauthorized fast travel. | 地主可自由設定是否允許未信任的訪客在自己花域內使用 `/sethome` 設家。 | `/csethome [on\|off\|status]` |
| **PvP Combat Toggle** | Enable or disable player vs player combat inside the claim with real-time feedback. | 地主可自主開啟或關閉領地內的 PVP 對戰狀態，保護和平玩家。 | `/pvp` (`/pvpinclaim`) |
| **Natural Mob Filters** | Selectively toggle natural spawning for 5 mob categories (Hostile, Raider, Phantom, Slime, Ambient) without affecting spawners or breeding. | 分類開關自然生怪（一般敵對 / 掠奪者 / 夜魅 / 史萊姆 / 蝙蝠），不影響生怪磚與繁殖。 | `/cmob [category]` |
| **Fast Claim Teleport** | Instantly teleport to claim centers or custom spawn locations using Folia asynchronous region scheduling. | 透過 Folia 異步排程安全瞬間傳送至花域中心或自訂落腳點。 | `/claimtp [id\|alias]` (`/ctp`) |
| **Custom Spawn Point** | Claim owners can define their exact standing location as the landing target for `/ctp`. | 地主可將當前站立點設為 `/ctp` 傳送落腳點，隨時自由清除或恢復中心。 | `/cspawn [set\|clear]` |
| **Claim Aliases & Naming** | Assign memorable names (e.g. `home`, `farm`, `shop`) to claims for convenient teleportation and recognition. | 為領地設定自訂別名（如「主家」、「農場」），支援中英文並可直接用於傳送。 | `/cname [alias\|clear]` |
| **Interactive GUI Menu** | Chest GUI for intuitive claim settings, renaming, spawn points, and toggle adjustments. | 視覺化箱子選單，直觀管理花域狀態、改名、落腳點、TNT、PvP 與各類生物生成。 | `/csettings [id\|alias]` |
| **Global Admin Panel** | Multi-page paginated administrative GUI to inspect, filter, search, teleport, edit, or purge all server claims. | 全伺服器管理員面板，具備分頁、篩選、即時搜尋、強制傳送、改名與刪除功能。 | `/cadmin [list\|tp\|delete...]` |
| **Multi-Language (i18n)** | Full localization support with automatic Minecraft client locale matching (`zh_TW`, `en_US`, etc.). | 完整的國際化多語言架構，依照玩家客戶端語言自動切換繁中與英文。 | Configurable |

---

## 📋 Requirements / 環境需求

* **Minecraft Core**: Paper, Folia, or Lecithin **26.2** (or compatible Paper API).
* **Java Runtime**: **Java 25**.
* **Dependencies**: [GriefPrevention](https://github.com/TechFortress/GriefPrevention) (Soft dependency).
* **No external proprietary libraries required** — 100% standalone and open-source.

---

## 🎮 Commands & Permissions / 指令與權限

### Player Commands / 玩家指令

| Command / 指令 | Aliases / 別名 | Permission / 權限 | Description / 說明 |
|---|---|---|---|
| `/csettings [id\|alias]` | `/claimsettings`, `/cmenu`, `/claimgui` | `griefpreventionaddon.user` | Open claim settings GUI / 開啟花域設定選單 |
| `/ctnt [on\|off\|status]` | `/claimtnt` | `griefpreventionaddon.tnt` | Toggle or check TNT explosion damage / 切換 TNT 爆炸破壞 |
| `/csethome [on\|off\|status]` | `/claimsethome`, `/sethomeinclaim` | `griefpreventionaddon.sethome` | Toggle visitor `/sethome` permission / 切換訪客設家權限 |
| `/pvp` | `/pvpinclaim`, `/cpvp` | `griefpreventionaddon.user` | Toggle claim PvP combat / 切換領地 PVP 狀態 |
| `/cmob [category]` | `/claimmobs` | `griefpreventionaddon.user` | Toggle mob natural spawning categories / 切換生物自然生成 |
| `/claiminfo` | 無 | `griefpreventionaddon.user` | View info & actions for current claim / 查詢當前領地資訊 |
| `/claimtp [id\|alias]` | `/ctp`, `/claimteleport` | `griefpreventionaddon.tp` | Teleport to claim center or custom spawn / 傳送至花域 |
| `/claimname [alias\|clear]` | `/cname`, `/claimalias` | `griefpreventionaddon.user` | Set or clear custom claim alias / 設定或清除領地別名 |
| `/claimspawn [set\|clear]` | `/cspawn`, `/csetspawn` | `griefpreventionaddon.spawn` | Set current spot as claim spawn point / 設定花域落腳點 |
| `/claimslist` | `/claims`, `/myclaims` | `griefpreventionaddon.user` | List all owned claims with teleport buttons / 列出名下花域 |

### Admin Commands & Permissions / 管理員指令與權限

| Command / 指令 | Permission / 權限 | Description / 說明 |
|---|---|---|
| `/cadmin` | `griefpreventionaddon.admin` | Open global claims admin GUI / 開啟全服花域管理面板 |
| `/cadmin list [player]` | `griefpreventionaddon.admin` | List all claims or claims of a specific player / 檢視特定玩家或全部花域 |
| `/cadmin tp <id\|alias>` | `griefpreventionaddon.admin` | Force teleport to any claim / 強制傳送至任意花域 |
| `/cadmin delete <id>` | `griefpreventionaddon.admin` | Force delete claim and purge database records / 強制刪除花域與設定 |
| `/cadmin name <id> <alias\|clear>` | `griefpreventionaddon.admin` | Modify alias of any claim / 設定或清除任意花域別名 |
| `/cadmin set <id> <key> <true\|false>` | `griefpreventionaddon.admin` | Force toggle a claim setting key / 強制設定花域開關數值 |

---

## ⚙️ Configuration & Localization / 設定與多語言

The plugin stores configuration in `plugins/GriefPreventionAddon/config.yml`:

```yaml
locale:
  # Default fallback language (zh_TW, en_US, etc.)
  default-language: "zh_TW"
  # Auto detect and match player client locale
  auto-detect-client-locale: true

tnt:
  default-allowed: false

pvp:
  default-allowed: false

sethome:
  default-allowed: false
```

Language files are located in `plugins/GriefPreventionAddon/languages/`:
- `messages_zh_TW.yml` (Traditional Chinese)
- `messages_en_US.yml` (English)

All user-facing messages, GUI items, titles, and card descriptions use MiniMessage formatting and can be customized without compiling.

---

## 🛠️ Building & Testing / 建置與測試

Build requirements: JDK 25.

```bash
# Clone the repository
git clone https://github.com/TinyYana/Lycohinya.git
cd Lycohinya/LycohinyaPlugins/GriefPreventionAddon

# Run full unit tests
./gradlew test

# Compile and package standalone shadowJar
./gradlew build
```

The output JAR will be generated in `build/libs/GriefPreventionAddon-0.3.1.jar`.

---

## 📄 License / 授權條款

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.  
See the [LICENSE](LICENSE) file for details.

Developed by [TinyYana](https://tinyyana.com).
