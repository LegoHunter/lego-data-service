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
- Missing resources return structured `404` responses; validation and business-rule failures return structured `400` responses.

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
- Draft creation may omit `unitPrice`; unpriced drafts are eligible for Pricing Plane onboarding but blocked from sync readiness with `MISSING_UNIT_PRICE`.
- A request may set `updateSaleIntentToSellable=true` to flip an existing inventory row to `SELLABLE` while creating the local draft.
- An inventory row may have only one open local draft per marketplace.
- `LISTING_CREATE` sync request creation requires a ready local draft, positive `unitPrice`, no existing remote BrickLink inventory id, and no active duplicate `PENDING` or `CLAIMED` `LISTING_CREATE` request.
- In non-production environments, BrickLink draft details are forced to stockroom-only using `lego.marketplace-listing-drafts.non-prod-bricklink-stockroom-id`.
- The intended BrickLink stockroom mapping is `sandbox=C`, `dev=B`, and `prod=A`.
- The default unprofiled/local stockroom is `C` unless overridden with `MARKETPLACE_LISTING_DRAFTS_NON_PROD_BRICKLINK_STOCKROOM_ID`.
- Missing `item_inventory_photos` for a `SELLABLE` inventory row is reported as a readiness warning, not a blocker.
