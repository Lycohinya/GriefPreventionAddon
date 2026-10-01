# GriefPreventionAddon

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Minecraft Paper](https://img.shields.io/badge/Minecraft-Paper%20%2F%20Lecithin%2026.2-brightgreen.svg)](https://papermc.io/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)](https://kotlinlang.org/)

**A modern, feature-rich companion addon for [GriefPrevention](https://github.com/TechFortress/GriefPrevention).**  
Provides per-claim **and per-subdivision** rules (inherit / allow / deny) for TNT, PvP, guest `/sethome`, natural mob spawning and fire, a claim menu that covers most everyday GriefPrevention management (scope switching, rules, trust, alias, landing point, teleport, subdivide/resize guidance, abandon), and a global administrative panel.

Supports **Paper**, **Lecithin 26.2**, and Paper API compatible server cores with asynchronous thread safety, zero hard dependencies on internal libraries, and full multi-language (i18n) localization with auto-detection of player client locales.

---

## 🌟 Features / 主要功能

| Feature | Description (English) | 說明 (繁體中文) | Command |
|---|---|---|---|
| **Subdivision-scoped rules** | Every rule below can be set on the main claim or on any subdivision: inherit / allow / deny. A subdivision without its own value follows the main claim; a main claim without its own value follows the server default. | 每一項規則都能分別設在主花域或子花域：繼承／允許／禁止。子花域沒設定跟主花域，主花域沒設定跟伺服器預設 | rule commands, `/csettings` |
| **TNT Explosion Control** | Allow or block TNT block damage per scope; GriefPrevention's native per-claim flag is synced for each scope. | 每個範圍各自決定 TNT 能不能炸壞方塊，GP 原生欄位逐範圍同步 | `/ctnt [on\|off\|inherit\|status]` |
| **Guest `/sethome` Control** | Whether untrusted visitors can use `/sethome`. | 未信任的訪客能不能在這裡設家 | `/csethome [on\|off\|inherit\|status]` |
| **PvP Combat Toggle** | PvP per scope; players whose effective value changed are notified. | PVP 依範圍開關，實際值有變的人會收到通知 | `/pvp [on\|off\|inherit\|status]` |
| **Natural Mob Filters** | 5 mob categories (Hostile, Raider, Phantom, Slime, Ambient) without affecting spawners or breeding. | 分類開關自然生怪，不影響生怪磚與繁殖 | `/cmob [category] [on\|off\|inherit]` |
| **Fire** | Fire spread and fire block damage per scope (see "Fire" below). | 火焰蔓延、火燒方塊依範圍設定（見下方「火焰」） | `/cfire [spread\|burn] [on\|off\|inherit]` |
| **Fast Claim Teleport** | Instantly teleport to claim centers or custom spawn locations with safe height and region resolution. | 安全瞬間傳送至領地中心或自訂落腳點。 | `/claimtp [id\|alias]` (`/ctp`) |
| **Custom Spawn Point** | Claim owners can define their exact standing location as the landing target for `/ctp`. | 地主可將當前站立點設為 `/ctp` 傳送落腳點，隨時自由清除或恢復中心。 | `/cspawn [set\|clear]` |
| **Claim Aliases & Naming** | Assign memorable names (e.g. `home`, `farm`, `shop`) to claims for convenient teleportation and recognition. | 為領地設定自訂別名（如「主家」、「農場」），支援中英文並可直接用於傳送。 | `/cname [alias\|clear]` |
| **Claim Menu** | Overview / Rules / Trust & members / Management tabs. Opened inside a subdivision it targets that subdivision; scope picker for the main claim and subdivisions. Works with and without the resource pack. | 總覽／環境與保護／信任與成員／花域管理四個分頁。站在子花域打開就是那個子花域，可切換範圍；有沒有資源包都能完成 | `/csettings [id\|alias]` |
| **Global Admin Panel** | Multi-page paginated administrative GUI to inspect, filter, search, teleport, edit, or purge all server claims. | 全伺服器管理員面板，具備分頁、篩選、即時搜尋、強制傳送、改名與刪除功能。 | `/cadmin [list\|tp\|delete...]` |
| **Multi-Language (i18n)** | Full localization support with automatic Minecraft client locale matching (`zh_TW`, `en_US`, etc.). | 完整的國際化多語言架構，依照玩家客戶端語言自動切換繁中與英文。 | Configurable |

---

## 📋 Requirements / 環境需求

* **Minecraft Core**: **Paper**, **Lecithin 26.2**, or compatible **Paper API** server implementations.  
  *(Note: Upstream GriefPrevention does not run natively on standard Folia without compatibility modifications; multithreaded server execution requires Lecithin or a Paper-compatible environment).*
* **Java Runtime**: **Java 25**.
* **Dependencies**: [GriefPrevention](https://github.com/TechFortress/GriefPrevention) 16.18.x (soft dependency; runtime verified against 16.18.7) and **LycoLib** (hard dependency: menu shell, icons, glyph backgrounds).

---

## 🎮 Commands & Permissions / 指令與權限

### Player Commands / 玩家指令

| Command / 指令 | Aliases / 別名 | Permission / 權限 | Description / 說明 |
|---|---|---|---|
| `/csettings [id\|alias]` | `/claimsettings`, `/cmenu`, `/claimgui` | `griefpreventionaddon.user` | Open the claim menu / 開啟花域選單 |
| `/ctnt [on\|off\|inherit\|status]` | `/claimtnt` | `griefpreventionaddon.tnt` | TNT block damage of the scope you stand in / 腳下範圍的 TNT |
| `/csethome [on\|off\|inherit\|status]` | `/claimsethome`, `/sethomeinclaim` | `griefpreventionaddon.sethome` | Visitor `/sethome` / 訪客設家 |
| `/pvp [on\|off\|inherit\|status]` | `/pvpinclaim`, `/cpvp` | `griefpreventionaddon.user` | PvP / 玩家對戰 |
| `/cmob [category] [on\|off\|inherit]` | `/claimmobs` | `griefpreventionaddon.user` | Natural spawning per category / 分類自然生成 |
| `/cfire [spread\|burn] [on\|off\|inherit]` | `/claimfire` | `griefpreventionaddon.user` | Fire spread / fire block damage / 火焰蔓延、火燒方塊 |
| `/claiminfo` | 無 | `griefpreventionaddon.user` | View info & actions for current claim / 查詢當前領地資訊 |
| `/claimtp [id\|alias]` | `/ctp`, `/claimteleport` | `griefpreventionaddon.tp` | Teleport to claim center or custom spawn / 傳送至領地 |
| `/claimname [alias\|clear]` | `/cname`, `/claimalias` | `griefpreventionaddon.user` | Set or clear custom claim alias / 設定或清除領地別名 |
| `/claimspawn [set\|clear]` | `/cspawn`, `/csetspawn` | `griefpreventionaddon.spawn` | Set current spot as claim spawn point / 設定領地落腳點 |
| `/claimslist` | `/claims`, `/myclaims` | `griefpreventionaddon.user` | List all owned claims with teleport buttons / 列出名下領地 |

### Admin Commands & Permissions / 管理員指令與權限

| Command / 指令 | Permission / 權限 | Description / 說明 |
|---|---|---|
| `/cadmin` | `griefpreventionaddon.admin` | Open global claims admin GUI / 開啟全服領地管理面板 |
| `/cadmin list [player]` | `griefpreventionaddon.admin` | List all claims or claims of a specific player / 檢視特定玩家或全部領地 |
| `/cadmin tp <id\|alias>` | `griefpreventionaddon.admin` | Force teleport to any claim / 強制傳送至任意領地 |
| `/cadmin delete <id>` | `griefpreventionaddon.admin` | Force delete claim and purge database records / 強制刪除領地與設定 |
| `/cadmin name <id> <alias\|clear>` | `griefpreventionaddon.admin` | Modify alias of any claim / 設定或清除任意領地別名 |
| `/cadmin set <id> <key> <allow\|deny\|inherit\|true\|false>` | `griefpreventionaddon.admin` | Set a rule on an exact scope (true/false = stored value, kept for old scripts); unknown ids are rejected / 設定精確範圍的規則，不存在的 id 直接拒絕 |
| `/cadmin inspect <id>` · `/cadmin inspect at <world> <x> <y> <z>` | `griefpreventionaddon.admin` | Diagnostics: every rule's local / effective / source, GP explosives flag, trust list / 診斷：每項規則的本地值、實際值、來源與信任 |
| `/cadmin fire` | `griefpreventionaddon.admin` | Diagnostics: fire takeover state and LOWEST handlers / 診斷：火焰接管狀態 |
| `/cadmin spawntest <type> [reason]` | `griefpreventionaddon.admin` | Fire a real spawn at your feet and report the filter verdict / 在腳下實際觸發一次生成並回報過濾結果 |

Rule commands act on the exact scope you stand in (a subdivision if you stand in one). Without an argument they flip the current effective value into an explicit value of that scope. GriefPrevention's own `/claimexplosions` is redirected to the same TNT rule so the two cannot disagree.

規則指令作用在腳下的精確範圍；不帶參數 = 把目前實際值翻過來寫成這裡自己的值。GP 原生 `/claimexplosions` 轉到同一個 TNT 規則

---

## 🧭 Scoped rules / 主花域與子花域規則

- **Three states.** A stored row is an explicit value (`'true'`/`'false'`); no row = inherit. Resolution: subdivision value → main claim value → server default (`defaults.*` in config.yml). Changing the main claim only affects subdivisions still inheriting; "inherit" on a subdivision deletes only that subdivision's row.
- **Who can change rules:** the main claim owner (a subdivision belongs to the same owner) and holders of `griefpreventionaddon.settings.others` / `admin`. GriefPrevention Build / Container / Access / Manage trust does **not** grant rule editing. Trust itself is edited in GriefPrevention's own data from the menu, with the same checks as `/trust`, `/untrust`, `/restrictsubclaim` and a `TrustChangedEvent`.
- **Aliases and landing points** belong to the main claim (they name and locate the whole land for `/ctp`). Inside a subdivision the menu shows and edits the main claim's values, labelled as such.
- **Lifecycle:** deleting a subdivision purges only its id (GriefPrevention deletes children one by one when a main claim is removed, so the whole tree is cleaned). Resize and transfer keep the id, so rules stay with the land; every menu click re-fetches the claim by id and re-checks permission.
- **Hot paths** (spawn, explosion, fire) read only in-memory caches; writes are queued to one SQLite writer thread.

## 🔥 Fire / 火焰

Two rules: **fire spread** (`BlockSpreadEvent` to fire, `BlockIgniteEvent` SPREAD and LAVA) and **fire block damage** (`BlockBurnEvent`). Player ignition (flint and steel, fire charges, arrows), lightning, explosions and every event whose target is in the wilderness stay exactly as GriefPrevention handles them.

The addon takes GriefPrevention's `BlockEventHandler` listener off those three handler lists and registers its own LOWEST handler. Target inside a claim: the addon decides (it only ever cancels or extinguishes, never un-cancels another plugin). Anything else: the original GriefPrevention listener is called unchanged. Different owners (or wilderness into a claim) are always denied, as in GriefPrevention. On disable the original listener is put back; `fire.takeover: false` leaves GriefPrevention untouched and shows the fire rules as unavailable. The `gp` defaults reproduce GriefPrevention exactly: spread = `FireSpreads && FireSpreadsInClaims`, damage = `FireDestroys && FireDamagesInClaims`. When the world gamerule `fire_spread_radius_around_player` is 0 the menu notes that fire cannot spread there.

---

## 🧟 Natural Mob Filter Policy / 自然生怪過濾政策

`/cmob` 只攔「世界自己跑出來的生怪」，判準是 `CreatureSpawnEvent` 的來源：

| | Spawn reasons |
|---|---|
| **攔截 / Blocked** | `NATURAL`、`SLIME_SPLIT`、`PATROL`、`RAID`、`VILLAGE_INVASION`、`REINFORCEMENTS`、`JOCKEY`、`NETHER_PORTAL`、`TRAP` |
| **放行 / Allowed** | 生怪磚與試煉生怪磚、生怪蛋、指令與插件生成、繁殖與農場產出（含蜂巢、剪毛、水桶）、既有生物的轉化（感染／溺屍化／凍結／豬布林殭屍化／治癒等） |

分類依伺服器端的實體型別與 `SpawnCategory` 判定，不寫死生物名單：

| 類別 | 涵蓋 |
|---|---|
| 掠奪者 | `Raider` |
| 夜魅 | `Phantom`（以半徑 40 格內最近玩家的位置為領地判定錨點） |
| 史萊姆 | 敵對的 `AbstractCubeMob`（史萊姆、岩漿立方怪，以及日後新增的敵對立方生物）；可養殖的被動立方生物不受影響 |
| 蝙蝠 | `Ambient` 或 `SpawnCategory.AMBIENT` |
| 一般敵對 | `Enemy` 或 `SpawnCategory.MONSTER` |

生怪過濾把領地視為一整根柱子（忽略高度，子花域同樣適用），領地底下洞穴層的自然生怪同樣受管；TNT、PVP 與資訊查詢維持 GriefPrevention 原本的 3D 領地判定。區塊生成與區塊載入時就已存在的生物不會發出 `CreatureSpawnEvent`（`CHUNK_GEN` 在 Paper 26.2 已停用），不在 `/cmob` 涵蓋範圍。

---

## ⚙️ Configuration & Localization / 設定與多語言

The plugin stores configuration in `plugins/GriefPreventionAddon/config.yml`:

```yaml
locale:
  # Default fallback language (zh_TW, en_US, etc.)
  default-language: "zh_TW"
  # Auto detect and match player client locale
  auto-detect-client-locale: true

# value used when a main claim has no value of its own: allow / deny / gp (fire only)
defaults:
  tnt: deny
  pvp: deny
  allow-sethome: deny
  no-hostile-spawn: allow   # allow = mobs spawn
  fire-spread: gp
  fire-burn: gp

fire:
  takeover: true   # false = GriefPrevention handles fire exactly as before
```

Language files are located in `plugins/GriefPreventionAddon/languages/`:
- `messages_zh_TW.yml` (Traditional Chinese)
- `messages_en_US.yml` (English)

All user-facing messages, GUI items, titles, and card descriptions use MiniMessage formatting and can be customized without compiling.

---

## 🗺️ Maintenance map / 維護地圖

| To do | Touch |
|---|---|
| Add a scoped boolean rule | one entry in `policy/ClaimFlags`; `flag.<langKey>.name/allow/deny` per language; the listener that enforces it (`resolver.allows(flag, bridge.scopeOf(claim))`); optional `defaults.<key>`. Commands, the Rules tab, `/cadmin set\|inspect` and the admin list pick it up automatically |
| Add a rule command | `plugin.yml` + one line in `GriefPreventionAddonPlugin.flagCommands` |
| Add a menu page or band | `gui/ClaimMenuPages` returns a `PageModel`; geometry is `gui/RailLayout`; drawing is `gui/ClaimMenuRenderer` (rail backgrounds `shell_railgroup_6_*`, plain title + glass without the pack) |
| Change how a value resolves | `policy/ClaimPolicyResolver` only |
| Trust behaviour | `trust/ClaimTrustService` (mirrors GriefPrevention 16.18.7 commands) |
| Storage / migration | `storage/ClaimSettingsStore` (`addon_meta` flags make each step re-runnable) |
| Runtime check | `tools/mineflayer/gpa-claim-scope-test.js` in the Lycohinya repo |

## ⬆️ Upgrade & rollback / 升級與回退

- **1.0.0-rc.3 (schema v2)** runs on first start: backup `backups/data.db.bak-v1-*` (only if there is data); the LycoServerTweaks import is marked done when the DB was created by an older version (older versions re-imported on every start and could resurrect values players had turned off); boolean rows stored on subdivision ids are removed. Older versions never applied those rows, so effective behaviour is unchanged.
- **Rollback:** put the previous jar back. Older versions read the v2 table correctly for main claims (`'false'` rows count as off); subdivision overrides and fire rules are ignored. Restoring the backup is not required.

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
