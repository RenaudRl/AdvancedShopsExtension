# Changelog

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
