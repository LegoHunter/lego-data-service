# lego-data-service Runbook

This runbook describes the operational contract of `lego-data-service` for inventory
intake, local marketplace-listing drafts, readiness evaluation, pricing-plane handoff,
and outbound sync-request creation. It is intentionally focused on the service API and
the local database state. `lego-data-ingress` owns BrickLink pricing crawls, pricing
decisions, local price application, and calls to the BrickLink REST/AJAX APIs.

## 1. Service boundary and end-to-end flow

The service is the system-of-record API for the following flow:

```text
POST /api/v1/inventory
        |
        v
item_inventory + primary BrickLink catalog link
        |
        +--> correct inventory and promote sale intent to SELLABLE
        |
        v
POST /api/v1/marketplace-listings
        |
        v
local marketplace_listing DRAFT
        |
        +--> manual positive price
        |       |
        |       +--> readiness --> LISTING_CREATE sync request
        |
        +--> non-fixed, unitPrice = null
                |
                +--> lego-data-ingress Pricing Plane
                        crawl -> snapshot -> decision -> apply-readiness
                        -> local price apply -> LISTING_CREATE sync request
                        -> BrickLink create -> local ACTIVE
```

Important boundaries:

- Intake creates inventory and accounting data. It does not create a marketplace listing
  and does not queue a BrickLink mutation.
- Inventory corrections and sale-intent changes update local state only.
- Listing-draft creation persists a local draft and BrickLink-specific draft details. It
  does not call BrickLink.
- The service creates durable `marketplace_listing_sync_request` rows. Ingress consumes
  those rows and performs the remote operation.
- A local `DRAFT` is not proof that a BrickLink listing exists. The local row becomes
  `ACTIVE` only after ingress creates the remote inventory and verifies the response.

## 2. Runtime and environment preflight

Before using a non-local environment, confirm:

1. The service is connected to the intended database and `item_inventory` rows are from
   that environment.
2. The BrickLink catalog row and the item-to-catalog link exist for the inventory item.
3. The database permits `marketplace_listing.unit_price` to be null. This is required
   for an unpriced, non-fixed draft to enter the Pricing Plane. Verify it before testing
   the algorithmic initial-price path:

   ```sql
   select table_name,
          column_name,
          is_nullable,
          column_type
   from information_schema.columns
   where table_schema = database()
     and table_name = 'marketplace_listing'
     and column_name = 'unit_price';
   ```

   The expected value of `is_nullable` is `YES`. A database migration/deployer change,
   not an API payload workaround, is the correct fix for a `Column 'unit_price' cannot
   be null` insert failure. Do not seed an artificial price just to satisfy an old
   `NOT NULL` schema.
4. In non-production, the bound ingress profile is configured for the intended
   BrickLink stockroom. The service defaults to stockroom `C` in sandbox/local
   configuration, but the active deployment and ingress configuration are authoritative.
5. The base URL and credentials point to the intended environment. The service itself
   does not need BrickLink REST credentials for the listing workflow; those are consumed
   by ingress.

`application-sandbox.yml` for this service identifies the environment as `sandbox`,
sets `production=false`, and uses stockroom `C` as the non-production default. These
settings protect the local draft contract; final remote safety is enforced by ingress.

## 3. Inventory intake API

### Endpoint

```http
POST /api/v1/inventory
Content-Type: application/json
```

The request creates one acquisition transaction and one `item_inventory` row per item.
The important item fields are:

```json
{
  "itemNumber": "<BRICKLINK_ITEM_NUMBER>",
  "description": "<optional local description>",
  "boxNumber": 1,
  "newOrUsed": "N",
  "completeness": "C",
  "sealed": false,
  "builtOnce": false,
  "itemConditionCode": "<condition code>",
  "boxConditionCode": "<condition code>",
  "instructionsConditionCode": "<condition code>",
  "transactionTypeCode": "<transaction type>",
  "costs": [
    {
      "costTypeCode": "PRICE",
      "amount": 1.00,
      "currencyCode": "USD",
      "notes": "<optional>"
    }
  ],
  "forSale": false,
  "quantity": 1,
  "active": true
}
```

The outer request also needs a transaction date, parties, a supported platform, at
least one item, item/transaction costs, and payments. `platformName` and
`transactionPlatformName` are accepted aliases; if both are supplied they must match.

Intake invariants are deliberately strict:

- `inventoryItems`, `payments`, and transaction `costs` are non-empty.
- Every physical item has quantity exactly `1`.
- New acquisition intake must use `forSale=false`; the service initializes sale intent
  to `KEEP`.
- Every item has exactly one `PRICE` cost. Transaction-level costs must not use
  `PRICE`.
- Cost and payment currencies and exchange rates must be compatible, and payments must
  balance the transaction and item costs.
- The BrickLink item number must resolve to a catalog item. Intake creates the primary
  `item_inventory_external_catalog_item` link.

The normal persisted initial state is:

| Field | Initial value | Meaning |
| --- | --- | --- |
| `active` | `true` | The inventory row is operationally active. |
| `forSale` | `false` | Legacy acquisition flag; it is not the marketplace gate. |
| `inventoryStateCode` | `AVAILABLE` | The physical item is available unless corrected. |
| `saleIntentCode` | `KEEP` | Intake does not decide that the owner wants to sell. |
| BrickLink catalog link | Primary | Used by future listing/pricing workflows. |
| Marketplace listing | None | Intake has no remote side effect. |
| Sync request | None | Intake has no BrickLink mutation queue entry. |

Save every returned `itemInventoryId`; it is the identifier used by all later API calls.

## 4. Inventory inspection and correction APIs

```http
POST  /api/v1/inventory/search
GET   /api/v1/inventory/{itemInventoryId}
GET   /api/v1/inventory/uuid/{uuid}
PATCH /api/v1/inventory/{itemInventoryId}/details
PATCH /api/v1/inventory/{itemInventoryId}/state
PATCH /api/v1/inventory/{itemInventoryId}/sale-intent
GET   /api/v1/inventory/{itemInventoryId}/marketplace-listings
GET   /api/v1/inventory/{itemInventoryId}/marketplace-readiness?marketplaceCode=BRICKLINK
```

For the listing workflow, verify:

```text
active = true
inventoryStateCode = AVAILABLE
saleIntentCode = SELLABLE
newOrUsed = N or U
completeness = C or I
primary BrickLink catalog link exists
```

Promote sale intent explicitly when possible:

```http
PATCH /api/v1/inventory/{itemInventoryId}/sale-intent
Content-Type: application/json

{
  "saleIntentCode": "SELLABLE",
  "saleIntentNote": "Approved for BrickLink listing"
}
```

`KEEP` and `UNDECIDED` are not listing-ready. Creating a draft can also promote the
item by sending `updateSaleIntentToSellable=true`, but an explicit preceding PATCH is
easier to audit.

Corrections do not create marketplace listings or sync requests. Changing a local
description, condition, cost, physical state, or sale intent does not call BrickLink.

## 5. Catalog-link verification

The draft API accepts a linked `externalCatalogItemId`, not an arbitrary BrickLink
catalog-table primary key. To inspect the mapping for one or more inventory rows:

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
where iieci.item_inventory_id in (<item_inventory_id> )
  and eci.external_service_id = 2
order by iieci.item_inventory_id, iieci.is_primary desc;
```

The service requires a BrickLink link for the requested draft. An explicit
`externalCatalogItemId` must belong to the inventory row and must be a BrickLink row;
when it is omitted, the service uses the primary BrickLink link.

`external_unique_key` is BrickLink's internal `idItem`, not the user-facing item
number. A null internal key does not automatically mean that the item is unmappable:
the ingress pricing crawler can use BrickLink `searchproduct.ajax` to resolve the item
number and item type, then persist the hydrated internal key. Inspect the crawl work
item and ingress logs if lookup is ambiguous, has no match, or returns an HTTP error.

## 6. Create a local BrickLink marketplace draft

### Canonical algorithmic-pricing onboarding request

Use a null or omitted `unitPrice` only for a non-fixed local draft that should receive
its initial price from the Pricing Plane. The database must already allow null.

```http
POST /api/v1/marketplace-listings
Content-Type: application/json
```

```json
{
  "itemInventoryId": 18097,
  "marketplaceCode": "BRICKLINK",
  "externalCatalogItemId": 22988,
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

Use the real item and linked catalog IDs for the request. For BrickLink sets,
`colorId=0` means that color does not apply; color-specific catalog items use the
appropriate positive color ID. The shared BrickLink color policy and the active
catalog item type are authoritative.

The service creates a `DRAFT` local listing. In a non-production environment it
normalizes the draft's stockroom details to the configured non-production stockroom and
marks the local remote-safety state as not yet verified.

### Manually priced or fixed-price request

If the operator already knows the price, send a positive value. `0` is invalid.

```json
{
  "itemInventoryId": 18097,
  "marketplaceCode": "BRICKLINK",
  "externalCatalogItemId": 22988,
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

The create request requires `itemInventoryId`, `marketplaceCode`, and `currencyCode`.
`unitPrice` accepts null only because the request is being used for non-fixed Pricing
Plane onboarding. A request with a missing catalog link returns a business validation
error such as:

```json
{
  "message": "Business rule validation failed",
  "errors": [
    {
      "field": null,
      "message": "Inventory item must have a BrickLink catalog link for the requested draft",
      "code": "BUSINESS_RULE"
    }
  ]
}
```

## 7. Draft read, update, and removal

```http
GET    /api/v1/marketplace-listings/{marketplaceListingId}
PATCH  /api/v1/marketplace-listings/{marketplaceListingId}
DELETE /api/v1/marketplace-listings/{marketplaceListingId}
```

The PATCH can update local title, description, private notes, price, currency, fixed
price, selected catalog link, and BrickLink draft details. A description-only update
can omit the `bricklink` object. If `bricklink` is supplied, send the complete current
writable BrickLink detail block; omitted nested fields can be overwritten with null.

For BrickLink human remarks, use `bricklink.remarks`. Do not copy or hand-author the
managed `[SYSTEM_BEGIN] ... [SYSTEM_END]` ownership block; ingress generates and
validates it during remote synchronization. `privateNotes` remain local operator notes
and are not sent to BrickLink.

Before the first remote create, PATCH changes are included in the `LISTING_CREATE`
payload. After a listing is `ACTIVE` and has a BrickLink inventory ID, PATCH still
changes local state but does not enqueue a remote metadata update. The current
`PRICE_UPDATE` path updates price and preserves/repairs managed system remarks; it does
not copy local description, title, private notes, or newly edited human remarks. Do not
change an active listing back to `DRAFT` to force `LISTING_CREATE`.

## 8. Readiness contract and status names

Evaluate readiness before creating a sync request:

```http
GET /api/v1/inventory/{itemInventoryId}/marketplace-readiness?marketplaceCode=BRICKLINK
GET /api/v1/marketplace-listings/{marketplaceListingId}
```

The response contains the inventory, local listing, BrickLink draft details, blocker
list, warning list, and `readyForMarketplaceSync`.

Common blockers:

| Code | Meaning and action |
| --- | --- |
| `UNSUPPORTED_MARKETPLACE` | Only `BRICKLINK` is implemented in this workflow. |
| `INVENTORY_INACTIVE` | Set the item active after correcting its state. |
| `INVENTORY_NOT_SELLABLE` | Promote sale intent to `SELLABLE`. |
| `INVENTORY_NOT_AVAILABLE` | Correct the physical inventory state to `AVAILABLE`. |
| `MISSING_PRIMARY_BRICKLINK_CATALOG_LINK` | Repair the item-to-catalog mapping. |
| `MISSING_MARKETPLACE_LISTING_DRAFT` | Create the local draft. |
| `INITIAL_PRICE_PENDING` | Non-fixed local `DRAFT` has no price; wait for Pricing Plane apply. |
| `MISSING_FIXED_UNIT_PRICE` | A null price is not valid for the current fixed/non-draft state. |
| `INVALID_UNIT_PRICE` | Price is zero or negative; supply a positive amount. |
| `MISSING_BRICKLINK_LISTING_DETAILS` | Add BrickLink details for a non-production draft. |
| `NON_PROD_BRICKLINK_STOCKROOM_REQUIRED` | Keep `isStockRoom=true`. |
| `NON_PROD_BRICKLINK_STOCKROOM_ID_REQUIRED` | Use the active non-production stockroom, normally `C` in sandbox. |
| `BRICKLINK_*_COLOR_*` | Use the color semantics required by the catalog item type. |
| `ACTIVE_SYNC_REQUEST_ALREADY_EXISTS` | Inspect the existing pending/claimed request before retrying. |
| `BRICKLINK_REMOTE_INVENTORY_ALREADY_EXISTS` | A create request is no longer valid; inspect the stored remote ID. |

Missing photos currently produce `MISSING_ITEM_INVENTORY_PHOTOS` as a warning, not a
sync blocker.

### Initial-price naming note

The implemented service blocker name is `INITIAL_PRICE_PENDING`. The proposed name
`INITIAL_FIXED_PRICE_PENDING` was discussed, but it is not the current code contract;
do not use that name in API clients or operational queries unless a future code change
renames it everywhere.

## 9. Sync preview and request APIs

```http
GET  /api/v1/marketplace-listings/{marketplaceListingId}/sync-request-preview
POST /api/v1/marketplace-listings/{marketplaceListingId}/sync-requests
GET  /api/v1/marketplace-listings/{marketplaceListingId}/sync-requests
GET  /api/v1/marketplace-listings/sync-requests/{marketplaceListingSyncRequestId}
PATCH /api/v1/marketplace-listings/sync-requests/{marketplaceListingSyncRequestId}/cancel
```

The preview must have no blockers before the POST. The POST body is optional; an empty
body or `{}` defaults to:

```json
{
  "syncRequestTypeCode": "LISTING_CREATE",
  "syncReasonCode": "MANUAL_LISTING_CREATE",
  "maxAttempts": 3
}
```

Only `LISTING_CREATE` is supported by the service endpoint. A single request addresses
one `marketplaceListingId`, and therefore one inventory item. There is no multi-item
variant of this POST; a client that wants a batch must enumerate listing IDs and inspect
each preview independently.

The service rejects a create request when:

- readiness has any blocker;
- the sync type is not `LISTING_CREATE`;
- a BrickLink remote inventory ID is already stored; or
- a pending/claimed create request already exists.

Only `PENDING` requests can be cancelled. A blocked or failed request should be
investigated first; fixing the underlying data does not automatically reset that old
request.

## 10. Pricing Plane handoff

For a non-fixed draft created with `unitPrice=null`:

1. The service reports `INITIAL_PRICE_PENDING` and manual sync-request creation remains
   blocked.
2. Ingress schedules or claims a `pricing_crawl_work_item` and calls BrickLink AJAX
   catalog/pricing endpoints.
3. Ingress persists `pricing_snapshot` and `pricing_snapshot_listing` rows.
4. The decision job writes a `pricing_decision`. A positive algorithmic price is stored
   in `final_price`; a `SUCCEEDED` crawl alone does not calculate or apply a price.
5. Apply-readiness writes `READY_TO_APPLY_INITIAL_PRICE` when the latest proposed
   decision meets the initial-price confidence/comparable/reason gates. There is no
   current-price delta baseline on this path.
6. Apply mode defensively rechecks that the row is still an unpriced local `DRAFT` with
   no remote inventory. It writes `marketplace_listing.unit_price` and queues
   `LISTING_CREATE` with a positive requested price and reason
   `INITIAL_PRICE_APPLIED`.
7. Ingress marketplace sync creates the BrickLink inventory and, after a safe read-back,
   changes the local listing to `ACTIVE`.

If an operator manually prices or publishes the draft before step 6, the initial-price
apply guard treats the decision as stale and must not overwrite the operator's change.

For an already priced, active listing, the normal path is a `PROPOSED` decision,
`READY_TO_APPLY`, local price update, and `PRICE_UPDATE` sync request. For
`fixedPrice=true`, the decision is recorded as `SKIPPED / FIXED_PRICE_OVERRIDE` and
does not replace the operator's price.

## 11. Database checks for a stuck listing

Use a listing ID as the primary trace key. These checks are read-only.

```sql
select marketplace_listing_id,
       item_inventory_id,
       listing_status_code,
       unit_price,
       currency_code,
       fixed_price,
       external_catalog_item_id,
       external_listing_id
from marketplace_listing
where marketplace_listing_id = <marketplace_listing_id>;
```

```sql
select pricing_crawl_work_item_id,
       marketplace_listing_id,
       work_status_code,
       attempt_count,
       next_attempt_at,
       last_error_message
from pricing_crawl_work_item
where marketplace_listing_id = <marketplace_listing_id>
order by pricing_crawl_work_item_id desc;

select pricing_snapshot_id,
       marketplace_listing_id,
       item_condition_code,
       completeness_code,
       comparable_count,
       captured_at
from pricing_snapshot
where marketplace_listing_id = <marketplace_listing_id>
order by pricing_snapshot_id desc;

select pricing_decision_id,
       marketplace_listing_id,
       pricing_snapshot_id,
       decision_status_code,
       reason_code,
       computed_price,
       final_price,
       comparable_count,
       confidence,
       applied_at
from pricing_decision
where marketplace_listing_id = <marketplace_listing_id>
order by pricing_decision_id desc;

select pricing_apply_readiness_id,
       marketplace_listing_id,
       pricing_decision_id,
       readiness_status_code,
       block_reason_code,
       current_price,
       proposed_price,
       delta_amount,
       delta_percent,
       comparable_count,
       confidence
from pricing_apply_readiness
where marketplace_listing_id = <marketplace_listing_id>
order by pricing_apply_readiness_id desc;
```

Interpret the states in order:

```text
SUCCEEDED crawl = source data was captured; it does not imply a price was applied
PROPOSED decision = a recommendation exists; it is not yet local truth
READY_TO_APPLY* = the recommendation passed apply gates; it is still not applied
marketplace_listing.unit_price changed = local price apply occurred
PENDING sync request = remote work is queued
SUCCEEDED sync request + ACTIVE listing = remote create/update completed and passed read-back safety
```

For the outbound queue:

```sql
select marketplace_listing_sync_request_id,
       marketplace_listing_id,
       sync_request_type_code,
       sync_request_status_code,
       sync_reason_code,
       requested_unit_price,
       remote_inventory_id,
       environment_code,
       attempt_count,
       max_attempts,
       next_attempt_at,
       last_error_message
from marketplace_listing_sync_request
where marketplace_listing_id = <marketplace_listing_id>
order by marketplace_listing_sync_request_id desc;
```

## 12. Known failure modes and safe responses

| Symptom | Interpretation | Response |
| --- | --- | --- |
| Structured business error says the item has no BrickLink catalog link | The requested catalog ID is not linked to this item, or no primary link exists. | Query the mapping, repair it, then recreate/readiness-check the draft. |
| `Column 'unit_price' cannot be null` from `POST /marketplace-listings` | The runtime schema is still `NOT NULL`; this is not a valid reason to seed a fake price. | Apply the database migration/deployer change and verify `is_nullable=YES`. |
| `INITIAL_PRICE_PENDING` | Expected for a non-fixed unpriced local DRAFT. | Let ingress crawl, decide, pass readiness, apply, and enqueue create. |
| Crawl work is `SUCCEEDED` but listing price is unchanged | Crawl captured market data only. | Inspect snapshot, decision, readiness, apply mode, and sync queue separately. |
| Body-less sync POST returned HTTP 500 in an older deployment | A null-safe logging defect could have happened after the database transaction committed. | Query the listing and sync-request row before retrying; do not blindly create a duplicate request. |
| Description/My Remarks PATCH appears successful but BrickLink is unchanged | Current PATCH is local-only for an `ACTIVE` listing. | Use it before first create if metadata must be included. An active-listing metadata sync type is not currently implemented. |
| POST is expected to handle several inventory IDs | The endpoint is one-listing-at-a-time. | Iterate one listing ID per preview/POST and enforce per-item safety. |
| Local listing is DRAFT but a remote ID exists | A create retry would be unsafe or a previous create had an incomplete local update. | Inspect remote read-back/safety metadata and reconcile the stored remote ID first. |

## 13. Deployment and operational handoff

Sandbox deployment is triggered by a feature-branch push in the current workflow. After
service changes are merged:

1. Switch the repository to `develop` and pull `origin/develop`.
2. Run `mvn clean install` and resolve any test/build failure before deployment.
3. Update the sandbox redeploy branch from the latest `develop` and push it when a
   sandbox rollout is required.
4. Watch the GitHub Action through completion and verify the deployed service pod,
   readiness, and logs.

If a feature-branch push creates no action run, check the repository workflow trigger,
the GitHub Actions run list, and the ARC runner pod logs/labels/image pull status. A
runner scheduling or image-pull failure is infrastructure; it does not indicate that
the service API rejected the deployment.

For application verification, check `/actuator/health`, `/actuator/info`, and the
service logs after rollout. For an end-to-end listing test, use a disposable inventory
row and verify the database trace before and after each Pricing Plane stage.

## 14. Operational safety rules

- Do not turn a local draft into a remote listing by changing database rows manually.
- Do not seed a fake unit price to bypass the initial-price workflow.
- Do not retry a 500 from a body-less sync POST until the database has been checked.
- Do not create a second open listing for the same inventory item and marketplace.
- Do not hand-edit managed BrickLink system remarks.
- Treat sandbox/dev BrickLink writes as real account mutations even though stockroom-only
  guardrails make them non-public.
- Use current runtime configuration and source behavior as the authority if an older
  playbook or chat backup disagrees; update the documentation with the same change.
