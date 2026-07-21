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

### 🖥️ GUI Integration
- **Full GUIExtension Integration**: Customizable layouts via the GUI/OmniGUI extension.
- **Buy/Sell Toggles**: Configurable per item, with adjustable refresh rates.
- **Transaction History**: Persistent storage via artifact.

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
- `build/libs/Shops-[Version].jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

---

## 📜 License
Licensed under the **MIT License**.
