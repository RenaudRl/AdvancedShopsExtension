# Changelog

## Unreleased

### Fields that were in the panel but not read

- `perPlayer` is a real per-player stock: each player trades against their own stock of an item,
  and a reset (policy or `resetNow`) refills everyone's. Limits keep their scope (`playerLimit` per
  player, `globalLimit` and the global buy/sell limits shared). The stock is stored in the shop
  artifact like the shared one; nothing is synchronised between servers.
- `defaultRefreshTicks` and the item `refreshTicks` redraw the stock shown by an open shop
  periodically (items with a finite stock only; the item's value replaces the default, 0 = the
  default, a default of 0 = never).
- Item `guiSlot` pins the item at that slot (row by row inside the content area), on every page;
  the other items keep flowing through the SHOP_ITEM markers around it. A slot outside the area
  falls back to the markers.
- Item `sellAllEnabled` is checked by sell all: when off, the action is refused with `cannotSellMessage`.
- The shop opening and its buttons now apply the full reset (stocks and limits) when it is due,
  instead of refilling the shared stock only.

## 1.10 — 2026-09-14

### Barter — prices paid in items

- Added `priceMode: ITEMS`. An article is sold against one or several items instead of currency:
  `itemPrice` on the item lists what it costs (an `ItemCost` = item + quantity per unit bought),
  `defaultItemPrice` on the shop covers every `ITEMS` article that declares none. The article's
  list replaces the shop's, it is never added to it.
- An `ITEMS` article with no price anywhere is refused and logged, never given away: an empty
  price is not a zero price.
- Payment is simulated on the whole inventory before anything is removed — two price lines
  targeting the same stacks cannot both claim it — and the bought item's space is checked before
  the withdrawal, since a barter has no refund. Catalogue items are matched by identity.
- Added `cannotAffordItemsMessage` (`{missing}`, `{amount}`) and the `{item_price}` lore token,
  one dash-prefixed line per requested item. `{buy}`/`{sell}` lines are dropped on such an
  article; `buy_price`/`sell_price` placeholders read `—`. "Buy max" works on barter articles.
- An `ITEMS` article cannot be sold back for currency: its sell price is zero.
- Rebuilt against OmniGUI v0.17.

## 1.7 - 2026-08-29

- Rebuilt against OmniGUI (GuiAndDialogs) v0.14.

## 1.6 — 2026-08-24

- Added the operator command tree `/tw shop admin` with shop listing, definition inspection,
  player session control, runtime reset, direct-item stock management and transaction history.
- Registered `typewriter.shop.admin` as an OP-only permission and kept
  `typewriter.shop.open` available to players by default.
- Added a manual reset entry point that shares the automatic reset behavior and recalculates the
  next scheduled reset.
- Fixed the public command permission registration so non-operators can open shops.

## 1.5 — 2026-08-21

- Rebuilt the extension against OmniGUI v0.13.

## 1.4 — 2026-08-12

- Added `baseMenuId`, `views`, `defaultViewId` and a content rectangle (`contentX`, `contentY`,
  `contentColumns`, `contentRows`) on `shop_definition`: a shop can now be a **tab of a shared
  chassis**, its layout pool and views merging underneath it, instead of only opening as its own
  inventory. Left empty, every existing shop keeps the exact standalone path it always took.
- A shop opened inside an extended chassis gets the ten-row canvas, like every other menu.
- Fixed a layout emptied of its markers being dropped from the pool. A shop screen whose whole
  grid is made of markers, or a still-empty tab, rendered as nothing at all — the chassis frame
  pointing at it found no layout to draw.
- Added `defaultNameFormat` and `defaultLoreFormat` on `shop_definition`. An item that declares
  no name or no lore inherits the shop's format; an item that declares its own keeps it
  untouched — the shop format only fills gaps, it never overrides an author's wording.
- Both formats accept the same tokens: `{item}` (the item's own name), `{buy}`, `{sell}`,
  `{stock}`, `{stock_max}`, `{currency}`. A lore line asking for an unavailable price
  (item not buyable/sellable, or price at zero) is dropped rather than rendered empty.
- Factored the price-token substitution shared by `priceLore` and the new default formats into
  a single pass, so the two can no longer drift apart.
- Added `ShopTransactionEvent`, fired once a purchase or a sale has completed. It is the seam
  other extensions plug into — a Discord relay, an audit log, an analytics pipeline — without
  Shops knowing they exist. Announced, never cancellable: it is emitted after the fact.
- Added `notificationWebhookId` on `shop_definition`. Shops does not resolve it and does not know
  what a webhook is; the id travels on the event for whichever extension handles Discord. A
  listener that throws is reported and the transaction stands — nothing undoes a paid purchase.
