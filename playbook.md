# lego-data-service Inventory-to-BrickLink API Playbook

This is the operator-facing sequence for taking one acquired inventory item through a
local draft and handing it to `lego-data-ingress` for BrickLink creation. It complements
[`runbook.md`](runbook.md), which contains the service boundary, failure modes, and
longer diagnostics.

Use placeholders such as `<base-url>`, `<item-inventory-id>`, and
`<marketplace-listing-id>` in a real request. The numeric IDs in examples are only
illustrative.

## Choose the price path first

There are two valid paths:

| Path | Draft price | What happens next |
| --- | --- | --- |
| Algorithmic initial price | `unitPrice: null`, `fixedPrice: false` | Ingress calculates and applies a positive initial price, then queues `LISTING_CREATE`. |
| Manual/fixed price | Positive `unitPrice`, usually `fixedPrice: true` | Readiness can create a `LISTING_CREATE` request immediately. Pricing records a fixed-price override. |

Never use `0` as a placeholder. A positive price is required before any BrickLink
`LISTING_CREATE` call, but the local non-fixed DRAFT may remain unpriced until the
Pricing Plane supplies that price.

## Step 1: Intake the item

```http
POST <base-url>/api/v1/inventory
Content-Type: application/json
```

The request must include a supported transaction platform, parties, date, payments,
transaction costs, and at least one item. Each item must have:

```json
{
  "itemNumber": "<BrickLink item number>",
  "boxNumber": 1,
  "newOrUsed": "N",
  "completeness": "C",
  "sealed": false,
  "builtOnce": false,
  "itemConditionCode": "<code>",
  "boxConditionCode": "<code>",
  "instructionsConditionCode": "<code>",
  "transactionTypeCode": "<code>",
  "costs": [{
    "costTypeCode": "PRICE",
    "amount": 1.00,
    "currencyCode": "USD"
  }],
  "forSale": false,
  "quantity": 1,
  "active": true
}
```

Intake starts the item as `AVAILABLE`, `KEEP`, active, and not for sale. It creates a
primary BrickLink catalog link but no marketplace listing and no sync request. Save the
returned `itemInventoryId`.

## Step 2: Correct and promote the item

Inspect the returned row and correct it before creating a listing:

```http
GET   <base-url>/api/v1/inventory/<item-inventory-id>
PATCH <base-url>/api/v1/inventory/<item-inventory-id>/details
PATCH <base-url>/api/v1/inventory/<item-inventory-id>/state
PATCH <base-url>/api/v1/inventory/<item-inventory-id>/sale-intent
```

The minimum listing gates are:

```text
active=true
inventoryStateCode=AVAILABLE
saleIntentCode=SELLABLE
primary BrickLink catalog link exists
```

Promote sale intent explicitly:

```http
PATCH <base-url>/api/v1/inventory/<item-inventory-id>/sale-intent
Content-Type: application/json

{
  "saleIntentCode": "SELLABLE",
  "saleIntentNote": "Approved for BrickLink listing"
}
```

This is still a local change. It does not call BrickLink.

## Step 3: Verify the catalog link

Use the service endpoint for a catalog lookup when you know the item number:

```http
GET <base-url>/api/v1/items/number/<bricklink-item-number>
GET <base-url>/api/v1/items/<external-catalog-item-id>
```

For an item-to-catalog check, use:

```sql
select iieci.item_inventory_id,
       iieci.external_catalog_item_id,
       iieci.is_primary,
       eci.external_item_key as bricklink_item_number,
       eci.item_type_code,
       eci.external_unique_key as bricklink_internal_id
from item_inventory_external_catalog_item iieci
join external_catalog_item eci
  on eci.external_catalog_item_id = iieci.external_catalog_item_id
where iieci.item_inventory_id = <item-inventory-id>
  and eci.external_service_id = 2;
```

`external_unique_key` is the BrickLink internal item ID used by pricing AJAX calls. If
it is null, do not invent a value: ingress can resolve it with `searchproduct.ajax`
using the item number and item type. Check the resulting crawl work item and logs.

## Step 4: Create the local draft

### Algorithmic initial price

First verify that `marketplace_listing.unit_price` is nullable in the target database.
Then call:

```http
POST <base-url>/api/v1/marketplace-listings
Content-Type: application/json

{
  "itemInventoryId": <item-inventory-id>,
  "marketplaceCode": "BRICKLINK",
  "externalCatalogItemId": <linked-bricklink-catalog-id>,
  "updateSaleIntentToSellable": false,
  "saleIntentNote": "Approved for BrickLink listing",
  "title": "Optional listing title",
  "description": "Optional buyer-facing description",
  "privateNotes": "Optional operator notes",
  "unitPrice": null,
  "currencyCode": "USD",
  "fixedPrice": false,
  "bricklink": {
    "colorId": 0,
    "bulk": 1,
    "isRetain": false,
    "isStockRoom": true,
    "stockRoomId": "C",
    "saleRate": 0,
    "remarks": "Optional human remarks"
  }
}
```

The response is a local `DRAFT`. Readiness should report `INITIAL_PRICE_PENDING`;
that is expected. Do not manually create a sync request at this point.

### Manual/fixed price

Use a positive value:

```json
{
  "itemInventoryId": <item-inventory-id>,
  "marketplaceCode": "BRICKLINK",
  "externalCatalogItemId": <linked-bricklink-catalog-id>,
  "unitPrice": 12.34,
  "currencyCode": "USD",
  "fixedPrice": true,
  "bricklink": {
    "colorId": 0,
    "bulk": 1,
    "isStockRoom": true,
    "stockRoomId": "C",
    "saleRate": 0,
    "remarks": "Optional human remarks"
  }
}
```

If the request says the item has no BrickLink catalog link, the catalog ID is not a
link for this item or the item has no primary BrickLink link. Repair the mapping first.

## Step 5: Check local readiness

```http
GET <base-url>/api/v1/inventory/<item-inventory-id>/marketplace-readiness?marketplaceCode=BRICKLINK
GET <base-url>/api/v1/marketplace-listings/<marketplace-listing-id>
```

Interpret the result:

```text
INITIAL_PRICE_PENDING       expected for an unpriced non-fixed DRAFT
MISSING_FIXED_UNIT_PRICE    current state requires a positive price
INVALID_UNIT_PRICE          price is zero or negative
MISSING_PRIMARY_*           repair the item-to-catalog relationship
NON_PROD_*_STOCKROOM_*      use the active sandbox/dev stockroom details
MISSING_ITEM_INVENTORY_PHOTOS warning only; it does not block current sync
```

The current implemented name is `INITIAL_PRICE_PENDING`; the discussed alternative
`INITIAL_FIXED_PRICE_PENDING` is not an API status in the current service.

## Step 6A: Let the Pricing Plane calculate the initial price

No service POST is needed after the unpriced draft is created. Ingress performs the
scheduled sequence:

```text
pricing_crawl_work_item
    -> pricing_snapshot + pricing_snapshot_listing
    -> pricing_decision
    -> pricing_apply_readiness (READY_TO_APPLY_INITIAL_PRICE)
    -> marketplace_listing.unit_price update
    -> LISTING_CREATE sync request
```

The key distinction is that `pricing_crawl_work_item.work_status_code=SUCCEEDED`
means the BrickLink source crawl succeeded. It does not mean that a price was decided,
applied, or sent to BrickLink.

Trace the listing with:

```sql
select marketplace_listing_id, listing_status_code, unit_price, fixed_price,
       external_listing_id
from marketplace_listing
where marketplace_listing_id = <marketplace-listing-id>;

select pricing_crawl_work_item_id, work_status_code, next_attempt_at,
       last_error_message
from pricing_crawl_work_item
where marketplace_listing_id = <marketplace-listing-id>
order by pricing_crawl_work_item_id desc;

select pricing_snapshot_id, comparable_count, captured_at
from pricing_snapshot
where marketplace_listing_id = <marketplace-listing-id>
order by pricing_snapshot_id desc;

select pricing_decision_id, decision_status_code, reason_code,
       computed_price, final_price, comparable_count, confidence, applied_at
from pricing_decision
where marketplace_listing_id = <marketplace-listing-id>
order by pricing_decision_id desc;

select pricing_apply_readiness_id, readiness_status_code, block_reason_code,
       current_price, proposed_price, delta_amount, delta_percent,
       comparable_count, confidence
from pricing_apply_readiness
where marketplace_listing_id = <marketplace-listing-id>
order by pricing_apply_readiness_id desc;
```

For an initial price, expect null current/delta values and a positive proposed price.
The ingress apply worker rechecks that the local listing is still an unpriced DRAFT with
no remote ID. If an operator changes it first, the algorithmic decision is stale and
must not overwrite the operator's state.

## Step 6B: Create a manually priced listing request

Preview first:

```http
GET <base-url>/api/v1/marketplace-listings/<marketplace-listing-id>/sync-request-preview
```

When `blockers` is empty, queue the request:

```http
POST <base-url>/api/v1/marketplace-listings/<marketplace-listing-id>/sync-requests
Content-Type: application/json

{}
```

An empty body defaults to `LISTING_CREATE`, reason `MANUAL_LISTING_CREATE`, and three
attempts. The endpoint handles one listing only; it cannot accept an array of inventory
IDs. For a batch, repeat preview and POST per listing and stop on each item's blockers.

Inspect the queue:

```http
GET <base-url>/api/v1/marketplace-listings/<marketplace-listing-id>/sync-requests
GET <base-url>/api/v1/marketplace-listings/sync-requests/<sync-request-id>
```

Only a `PENDING` request can be cancelled:

```http
PATCH <base-url>/api/v1/marketplace-listings/sync-requests/<sync-request-id>/cancel
Content-Type: application/json

{"reason":"Operator cancelled before remote create"}
```

## Step 7: Verify the remote create

The request is consumed by ingress, not by this service. In sandbox/dev it should be
created in the real BrickLink account but forced into the configured stockroom and
protected by the LegoHunter system remarks block. The successful end state is:

```text
sync_request_status_code = SUCCEEDED
marketplace_listing.listing_status_code = ACTIVE
bricklink_marketplace_listing.bricklink_inventory_id is populated
remote safety status = allowed/success
```

If a body-less sync POST once returned HTTP 500, query for an existing request before
retrying. A historical null-safe logging defect could return an error after the local
transaction had already committed.

## Step 8: Edit description or My Remarks

Before the first create, update the local draft and then preview/queue the create again:

```http
PATCH <base-url>/api/v1/marketplace-listings/<marketplace-listing-id>
Content-Type: application/json

{
  "description": "Updated buyer-facing description",
  "bricklink": {
    "colorId": 0,
    "bulk": 1,
    "isRetain": false,
    "isStockRoom": true,
    "stockRoomId": "C",
    "saleRate": 0,
    "remarks": "Updated human remarks"
  }
}
```

For description-only edits, omit `bricklink`. When `bricklink` is present, send the
complete current writable nested block so that omitted fields are not cleared. Do not
send the managed system remarks block.

After the listing is `ACTIVE`, this PATCH updates only local state. There is currently
no service sync-request type for a description-only or My-Remarks-only remote update;
the pricing `PRICE_UPDATE` path is for price changes and preserves managed safety
remarks, not arbitrary local metadata.

## Quick troubleshooting order

1. Check the inventory row: active, `AVAILABLE`, `SELLABLE`.
2. Check the primary BrickLink catalog link and selected catalog ID.
3. Check that exactly one open local listing exists.
4. Check price/fixed state and interpret `INITIAL_PRICE_PENDING` correctly.
5. Check the latest crawl, snapshot, decision, and readiness rows.
6. Check `marketplace_listing_sync_request` for `PENDING`, `CLAIMED`, `BLOCKED`, or
   `FAILED` and its `last_error_message`.
7. Check ingress configuration (`APPLY_LOCAL_AND_ENQUEUE_SYNC` and marketplace-sync
   `APPLY` in sandbox) and scheduled-job logs.
8. If remote creation may have succeeded, inspect the stored remote ID and BrickLink
   account before retrying.
