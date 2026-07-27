# lego-data-service
Lego Data Operations API.

### Deploys to Kubernetes

REST Service over lego-data

## Inventory Intake

`POST /api/v1/inventory` is the shared Direct REST Inventory Intake entry point for LEGO Item Acquisition workflows. Bruno/Postman, future Excel import, future UI flows, and future BrickLink order import should all map into this same acquisition service contract rather than introducing channel-specific create controllers.

Phase 1 behavior:

- Creates one acquisition `transactions` row.
- Requires at least one inventory item and one payment.
- Creates one `item_inventory` row per physical LEGO item.
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

- `POST /api/v1/inventory/search` searches owned inventory using an optional `ItemInventorySearchCriteria` request body with filters for item number, description, box number, inventory state, sale intent, active flag, physical item facts, condition codes, transaction date range, limit, and offset.
- `GET /api/v1/transactions/{transactionId}` returns the complete persisted acquisition transaction tree.
- `PATCH /api/v1/transactions/{transactionId}` updates transaction header fields such as date, parties, transaction platform, order id, and notes.
- `POST /api/v1/transactions/{transactionId}/costs`, `PUT /api/v1/transactions/{transactionId}/costs/{transactionCostId}`, and `DELETE /api/v1/transactions/{transactionId}/costs/{transactionCostId}` add, update, or delete one transaction-level cost row.
- `POST /api/v1/transactions/{transactionId}/payments`, `PUT /api/v1/transactions/{transactionId}/payments/{paymentId}`, and `DELETE /api/v1/transactions/{transactionId}/payments/{paymentId}` add, update, or delete one payment row.
- `PATCH /api/v1/transaction-items/{transactionItemId}` updates transaction-item correction fields.
- `POST /api/v1/transaction-items/{transactionItemId}/costs`, `PUT /api/v1/transaction-items/{transactionItemId}/costs/{transactionItemCostId}`, and `DELETE /api/v1/transaction-items/{transactionItemId}/costs/{transactionItemCostId}` add, update, or delete one item-level cost row.
- `PATCH /api/v1/inventory/{itemInventoryId}/physical` updates physical owned-item facts such as box, description, active flag, new/used, completeness, sealed, built-once, and condition codes.
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
