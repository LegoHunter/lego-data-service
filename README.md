# lego-data-service
Lego Data Operations API.

### Deploys to Kubernetes

REST Service over lego-data

## Inventory Intake

`POST /api/v1/inventory` is the shared Direct REST Inventory Intake entry point for LEGO Item Acquisition workflows. Bruno/Postman, future Excel import, future UI flows, and future BrickLink order import should all map into this same acquisition service contract rather than introducing channel-specific create controllers.

Phase 1 behavior:

- Creates one acquisition `transactions` row.
- Requires at least one inventory item and one payment.
- Creates one `item_inventory` row per owned LEGO item.
- Creates the primary BrickLink `item_inventory_external_catalog_item` link.
- Creates `transaction_item` rows.
- Creates `transaction_cost` rows for transaction-level fees only; transaction-level costs must not use `PRICE`.
- Creates `transaction_item_cost` rows for item-level costs; every transaction item must have one `PRICE` cost.
- Creates `payment` rows.
- Returns the persisted transaction tree, including costs, payments, transaction items, item inventory, catalog links, and item-level costs where present.
- Explicitly defaults new acquisition inventory to `inventoryStateCode=AVAILABLE`, `saleIntentCode=KEEP`, and legacy `forSale=false`.
- Does not create marketplace listings and does not enqueue marketplace sync requests.

Compatibility:

- `transactionPlatformName` is the forward-looking request field.
- `platformName` is temporarily accepted for backwards compatibility.
- Requests are rejected if both fields are provided with different values.
- `transactionDate` and `paymentDate` are date-only values. `transactionDateTime` is accepted as a temporary JSON alias for inbound compatibility, but it must contain a date-only value.

Phase 2 read/search/correction behavior:

- `POST /api/v1/inventory/search` searches owned inventory using an optional `ItemInventorySearchCriteria` request body with filters for item number, description, box number, inventory state, sale intent, active flag, owned-item details, condition codes, transaction date range, limit, and offset. Each result includes the `itemInventory` row plus the set of transactions in which that inventory row appears. Each transaction context includes the `transactions` row, transaction-level costs, the matching `transaction_item`, and that transaction item's costs. Search responses intentionally exclude payment data.
- `GET /api/v1/transactions/{transactionId}` returns the complete persisted acquisition transaction tree.
- `PATCH /api/v1/transactions/{transactionId}` updates transaction header fields such as date, parties, transaction platform, order id, and notes.
- `POST /api/v1/transactions/{transactionId}/costs`, `PUT /api/v1/transactions/{transactionId}/costs/{transactionCostId}`, and `DELETE /api/v1/transactions/{transactionId}/costs/{transactionCostId}` add, update, or delete one transaction-level cost row.
- `POST /api/v1/transactions/{transactionId}/payments`, `PUT /api/v1/transactions/{transactionId}/payments/{paymentId}`, and `DELETE /api/v1/transactions/{transactionId}/payments/{paymentId}` add, update, or delete one payment row.
- `PATCH /api/v1/transaction-items/{transactionItemId}` updates transaction-item correction fields.
- `POST /api/v1/transaction-items/{transactionItemId}/costs`, `PUT /api/v1/transaction-items/{transactionItemId}/costs/{transactionItemCostId}`, and `DELETE /api/v1/transaction-items/{transactionItemId}/costs/{transactionItemCostId}` add, update, or delete one item-level cost row.
- `PATCH /api/v1/inventory/{itemInventoryId}/details` updates owned-inventory details such as box, description, active flag, new/used, completeness, sealed, built-once, and condition codes.
- `PATCH /api/v1/inventory/{itemInventoryId}/state` updates `inventory_state_code` after validating the target state exists.
- `PATCH /api/v1/inventory/{itemInventoryId}/sale-intent` updates `sale_intent_code` and note after validating the target sale intent exists. This endpoint may set `UNDECIDED` for existing/legacy inventory rows; new acquisition intake still defaults to `KEEP`.

Phase 2 correction rules:

- Total payments must equal total transaction costs after every committed intake or correction.
- Total transaction costs include all transaction-level costs plus all transaction-item costs.
- All costs and payments in a transaction must use the same `currencyCode`.
- Transaction-level costs must not use `PRICE`; `PRICE` belongs on each transaction item.
- Each transaction item must have exactly one item-level `PRICE` cost.
- Cost/payment corrections are intentionally row-scoped add/update/delete operations.
- Corrections do not create marketplace listings and do not enqueue marketplace sync requests.
- Missing resources return structured `404` responses; validation and business-rule failures return structured `400` responses. A request the database refuses (a NOT NULL, unique or foreign-key constraint) returns a structured `409` with code `DATA_INTEGRITY` instead of a `500`; the database's reason is logged, not returned (HOMELAB-35).

Phase 3/4 marketplace listing draft/readiness behavior:

- `POST /api/v1/marketplace-listings` creates a local marketplace listing draft for an existing `item_inventory` row.
- `GET /api/v1/marketplace-listings/{marketplaceListingId}` returns one local listing draft with marketplace-specific details and readiness.
- `PATCH /api/v1/marketplace-listings/{marketplaceListingId}` updates one local listing draft.
- `DELETE /api/v1/marketplace-listings/{marketplaceListingId}` marks one local listing draft `REMOVED`.
- `GET /api/v1/marketplace-listings/{marketplaceListingId}/sync-request-preview` previews whether a local BrickLink draft can create a durable `LISTING_CREATE` sync request.
- `POST /api/v1/marketplace-listings/{marketplaceListingId}/sync-requests` creates a pending local `LISTING_CREATE` sync request when readiness has no blockers.
- `GET /api/v1/marketplace-listings/{marketplaceListingId}/sync-requests` lists sync requests for one marketplace listing.
- `GET /api/v1/marketplace-listings/sync-requests/{marketplaceListingSyncRequestId}` returns one sync request.
- `PATCH /api/v1/marketplace-listings/sync-requests/{marketplaceListingSyncRequestId}/cancel` cancels a pending sync request.
- `GET /api/v1/inventory/{itemInventoryId}/marketplace-listings` returns all local listing drafts for one inventory row.
- `GET /api/v1/inventory/{itemInventoryId}/marketplace-readiness?marketplaceCode=BRICKLINK` evaluates whether one inventory row is ready for future marketplace sync.

Phase 3/4 marketplace rules:

- Phase 4 supports BrickLink local drafts and local `LISTING_CREATE` sync request rows only.
- Phase 4 does not call BrickLink or eBay APIs.
- Draft creation requires active inventory, `saleIntentCode=SELLABLE`, `inventoryStateCode=AVAILABLE`, and a BrickLink catalog link.
- Draft creation may omit `unitPrice`; an unpriced non-fixed `DRAFT` is eligible for Pricing Plane onboarding and reports `INITIAL_PRICE_PENDING` until Pricing Plane applies a positive initial price. Unpriced active or fixed listings report `MISSING_FIXED_UNIT_PRICE` and require a positive operator-supplied price before marketplace sync.
- A request may set `updateSaleIntentToSellable=true` to flip an existing inventory row to `SELLABLE` while creating the local draft.
- An inventory row may have only one open local draft per marketplace.
- BrickLink draft details (`bricklink`) are patched field by field: `PATCH` changes only the fields it sends and keeps the rest, so a field cannot be cleared by sending `null`. `isRetain` and `isStockRoom` default to `false` when neither the request nor the stored row sets them (the table requires both); non-production then forces stockroom as below. An edit keeps the remote safety status lego-data-ingress recorded for the BrickLink lot; `NOT_VERIFIED` is filled in only on a row that has no status yet (HOMELAB-35).
- `LISTING_CREATE` sync request creation requires a ready local draft, positive `unitPrice`, no existing remote BrickLink inventory id, and no active duplicate `PENDING` or `CLAIMED` `LISTING_CREATE` request.
- In non-production environments, BrickLink draft details are forced to stockroom-only using `lego.marketplace-listing-drafts.non-prod-bricklink-stockroom-id`.
- The BrickLink stockroom mapping is `sandbox=C`, `dev=B`. Stockroom `A` is reserved for PROD's own deliberately hidden lots; PROD drafts are not forced into a stockroom (HOMELAB-20).
- There is no code default. At startup `MarketplaceListingDraftStockroomValidator` runs the shared rules from lego-data (`BricklinkStockroomPolicy`, `LegoEnvironment`). The service refuses to start if the environment code is unknown or disagrees with `production`, or if a lower environment has no stockroom, has `A`, or has another environment's stockroom.
- The environment is declared once per profile as `lego.environment.code` / `production`; the draft settings read it (`${lego.environment.code}`), and `MarketplaceListingDraftStockroomValidator` refuses to start if they are overridden to disagree. At startup the lego-data starter (`EnvironmentDatabaseVerifier`) checks `lego.environment` against the connected schema (`SELECT DATABASE()`): `sandbox` must be on `lego_sandbox`, `dev` on `lego_dev`, `prod` on `lego_prod`, and `local` on anything except `lego_prod` (HOMELAB-19/34). The service also refuses to start while the database cannot be reached, because the environment can't be verified. With `lego.data.enabled=false` there is no data source and nothing to check.
- The default unprofiled/local stockroom is `C` unless overridden with `MARKETPLACE_LISTING_DRAFTS_NON_PROD_BRICKLINK_STOCKROOM_ID`.
- Missing `item_inventory_photos` for a `SELLABLE` inventory row is reported as a readiness warning, not a blocker.

BrickLink color semantics:

- SET catalog items use `colorId=0` (`Not Applicable`). A missing SET color is normalized to `0`; a nonzero SET
  color is rejected.
- Color-specific BrickLink item types require a positive `colorId` before a draft can become sync-ready.
- The service applies this policy on both draft create/update and readiness evaluation, using the shared
  `lego-data` `BricklinkInventoryColorPolicy`.

## Lower-environment seeding CLI (HOMELAB-40)

`com.vattima.lego.inventory.seed.SeedCli` fills SANDBOX or DEV with realistic items copied from legacy
PROD item data. It runs on a workstation, never in the cluster, and starts no Spring context.

```shell
JAR=target/lego-data-service-1.0.0-SNAPSHOT.jar
SEED="java -Dloader.main=com.vattima.lego.inventory.seed.SeedCli -cp $JAR org.springframework.boot.loader.launch.PropertiesLauncher"

# 1. Read legacy PROD (read-only option file you supply) into a reviewable manifest
$SEED export --legacy-defaults-file=PATH/TO/legacy-ro.cnf --out=manifest.json [--buckets=USED,SEALED,NEW] [--limit=50] [--source-keys=..] [--item-numbers=..]
# 2. Check, then create the items and drafts
$SEED import --target=sandbox --manifest=manifest.json --dry-run
$SEED import --target=sandbox --manifest=manifest.json
# 3. Follow the pricing plane until each lot is VERIFIED
$SEED status --target=sandbox --seed-run-id=UUID
# 4. Remove a run again: its BrickLink lots, then its local rows
$SEED teardown --target=sandbox --seed-run-id=UUID --dry-run
$SEED teardown --target=sandbox --seed-run-id=UUID [--wait-minutes=60] [--poll-seconds=60]   # or --all
```

- **What is copied:** item data only: catalog number, new/used, completeness, sealed, built once, box and
  instructions grades, and cost. Listing and lot columns (remarks, price, stockroom, lot id, order, internal
  comments, the legacy uuid and description) are never read. Selection: unsold `SET` rows with quantity 1.
  Sealed items get item condition `SL` and default grades box `E` / instructions `M`. Unsealed items need
  both grades, otherwise they are skipped and reported.
- **What import does:** one acquisition transaction per run (seller party 209, buyer party 0, test box
  900). Each item carries a `seedSource` (`LEGO_PROD_LEGACY` + legacy `bl_inventory_id`). Then an unpriced,
  non-fixed BrickLink draft is created with sale intent `SELLABLE`. The tool never sends
  `LISTING_CREATE`. The pricing plane applies the initial price and creates the stockroom lot.
- **Guards:**
  - Targets are `sandbox` and `dev` only; the service also refuses `seedSource` in PROD.
  - At most 50 new items per run.
  - A ceiling on seeded items per environment (`--environment-ceiling`, default 200).
  - Items missing from the target's BrickLink catalog are reported and not sent.
- **Re-runs:** import skips source items already seeded and creates any missing draft, so re-running a
  manifest is safe and completes a run that stopped part way.
- **Teardown:** only items with a seed-source row, never in PROD, and only through the service
  (`/api/v1/inventory/{id}/seed-teardown`). Per item:
  1. `prepare` cancels its PENDING sync requests, queues a `LISTING_DELETE` for each listing that holds a
     BrickLink lot, and ends listings that hold none, so the pricing plane stops creating lots for them.
  2. The ingress marketplace sync job deletes each lot. It refuses in PROD, and deletes only a stockroom lot
     in this environment's stockroom whose system remarks block names this environment, listing and item.
     It then reads the lot back and accepts only "not found".
  3. When the service reports no lot and no active request, the item's local rows are deleted: listings,
     pricing history, sync requests, catalog links, acquisition line and costs, seed source, the item, and
     the acquisition transaction once its last item is gone.

  Items that never got a lot go straight to step 3. An item with photos, orders, sales, shipments or an
  eBay listing is reported `BLOCKED` and left alone. A lot that survives three `LISTING_DELETE` requests,
  or anything still waiting at `--wait-minutes`, is reported `STUCK`; the command then exits 1, and running
  it again picks up where it stopped.
