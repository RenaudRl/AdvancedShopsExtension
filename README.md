# Shops Extension

![Java Version](https://img.shields.io/badge/Java-25-orange)
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

Requires **Java 25**.

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
