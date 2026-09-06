# Shops Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20BTC--CORE-blue)

**Shops Extension** is a commerce system for **TypeWriter**, engineered for **BTC Studio** infrastructure. It provides a full-featured shop experience with dynamic pricing and stock handling.

---

## 🚀 Key Features

### 🛒 Dynamic Marketplace
- **Dynamic & Fixed Pricing**: Prices that fluctuate based on supply and demand, or stay fixed.
- **Categories & Item Pools**: Organize offers into categories, with randomized item pools.

### 📦 Stock & Limits
- **Stock Management**: Real-time tracking of shop stock levels.
- **Per-Player & Global Limits**: Buy/sell limits per player and globally.
- **Taxes & Promotions**: Global and per-item taxes, price trends, and promotions.

### 🏷️ Display Formats
- **Shop-wide defaults**: `defaultNameFormat` / `defaultLoreFormat` apply to every item that
  declares no name or lore of its own. An item that declares one keeps it untouched.
- **Tokens**: `{item}`, `{buy}`, `{sell}`, `{stock}`, `{stock_max}`, `{currency}`. A lore line
  asking for an unavailable price is dropped instead of rendering empty.

### 🔔 Transaction Events
- **`ShopTransactionEvent`**: fired once a purchase or a sale has completed. Other extensions —
  a Discord relay, an audit log, an analytics pipeline — plug in here without Shops knowing they
  exist. Announced after the fact, so it carries no cancellation.
- **`notificationWebhookId`**: the id a shop author writes on the definition. Shops does not
  resolve it and does not know what a webhook is; it travels on the event for whoever handles
  Discord.
- A listener that throws is reported and the transaction stands: nothing undoes a purchase that
  has already been paid for and delivered.

### 🖥️ GUI Integration
- **Full GUIExtension Integration**: Customizable layouts via the GUI/OmniGUI extension.
- **Buy/Sell Toggles**: Configurable per item, with adjustable refresh rates.
- **Transaction History**: Persistent storage via artifact.

### Admin Operations
- List and inspect loaded shop definitions.
- Open, refresh and close a shop session for an online player.
- Reset runtime stock and limits for a shop.
- Inspect and mutate stock for direct shop items.
- Inspect the latest transactions recorded for a player.

---

## ⚙️ Configuration

Shops Extension configuration is managed via TypeWriter's manifest system.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/AdvancedShopsExtension.git
cd AdvancedShopsExtension

# Build the project
./gradlew clean build
```

### Artifact Locations:
- `build/libs/TypeWriter-ShopsExtension-[Version].jar`

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/tw shop <id>` | `typewriter.shop.open` | Open a shop. Available to players by default. |
| `/tw shop admin` | `typewriter.shop.admin` | Show operator tools. OP-only by default. |
| `/tw shop admin list` | `typewriter.shop.admin` | List loaded shops. |
| `/tw shop admin info <shop>` | `typewriter.shop.admin` | Inspect shop and direct-item indexes. |
| `/tw shop admin open <shop> [target]` | `typewriter.shop.admin` | Open a shop for a player or selector target. |
| `/tw shop admin refresh|close [target]` | `typewriter.shop.admin` | Manage an open shop session. |
| `/tw shop admin reset <shop>` | `typewriter.shop.admin` | Reset runtime stock and limits. |
| `/tw shop admin stock <shop> <item> get|set|add|remove [amount]` | `typewriter.shop.admin` | Manage direct-item stock. |
| `/tw shop admin history [target]` | `typewriter.shop.admin` | Show the latest runtime transactions. |

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

---

## 📜 License
Licensed under the **MIT License**.

## Documentation

Full documentation available at [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/shop/).

---

## 📜 Licence

**GNU General Public License v3.0 or later** — [LICENSE](LICENSE) — with a
**linking exception** for the Typewriter engine — [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md).

| | |
|---|---|
| You may | Run it anywhere, **including on a monetised server**. Study it, modify it, use it as a base, and redistribute it — **even for a fee**. GPLv3 §4 explicitly allows charging for a copy. |
| You must | Publish the complete corresponding source of your version under GPLv3, preserve the copyright notices, and **state that you modified it and when** (§5(a)). |
| You may not | Ship a closed-source or proprietary version, relicense under stricter terms, or strip the attribution and present this work as your own — §8 terminates your rights automatically. |
| Marks | **"Born To Craft"** and **"BTC Studio"** are **not** covered by the GPL. Fork it freely, sell your fork if you like — but **rebrand it**. |

> Reselling this code is legally allowed and practically pointless: whoever buys a
> copy from you receives, under the GPL, the right to redistribute it for free.
> That is the protection — not a clause forbidding sale, which the GPL does not
> permit us to add.

### About Typewriter

This is a **third-party extension**. It uses the public extension API of the
[Typewriter](https://github.com/gabber235/Typewriter) engine by gabber235 and
contains none of its source. Born To Craft Studio is not affiliated with or
endorsed by the Typewriter project.

The engine itself is **not** free software — its licence forbids redistributing
it. **Get it from the Typewriter project, and never redistribute it**, including
inside a fork of this repository.

Full attribution, the statement of modifications required by §5(a), and the
trademark reservation are in **[NOTICE.md](NOTICE.md)**. Read it before
redistributing.

© 2026 Born To Craft Studio.
