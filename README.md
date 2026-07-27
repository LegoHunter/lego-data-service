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
