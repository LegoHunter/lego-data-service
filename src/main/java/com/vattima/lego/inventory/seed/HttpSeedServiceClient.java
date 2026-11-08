package com.vattima.lego.inventory.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SeedServiceClient} over {@link HttpClient}. Responses are read as JSON trees and only the
 * fields the tool needs are picked out, so a new field in a service DTO never breaks the tool.
 */
public class HttpSeedServiceClient implements SeedServiceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpSeedServiceClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public List<SeedSourceView> findSeedSources(String sourceSystem, String seedRunId) {
        StringBuilder path = new StringBuilder("/api/v1/inventory/seed-sources");
        String separator = "?";
        if (sourceSystem != null) {
            path.append(separator).append("sourceSystem=").append(encode(sourceSystem));
            separator = "&";
        }
        if (seedRunId != null) {
            path.append(separator).append("seedRunId=").append(encode(seedRunId));
        }
        List<SeedSourceView> result = new ArrayList<>();
        for (JsonNode node : get(path.toString())) {
            result.add(new SeedSourceView(
                    node.path("itemInventoryId").asInt(),
                    text(node, "sourceSystem"),
                    text(node, "sourceKey"),
                    text(node, "seedRunId")
            ));
        }
        return result;
    }

    @Override
    public boolean catalogItemExists(String itemNumber) {
        JsonNode node = get("/api/v1/items/number/" + encode(itemNumber));
        return node != null && !node.isNull() && !node.isMissingNode() && !node.isEmpty();
    }

    @Override
    public void addItemInventory(AddItemInventoryRequest request) {
        post("/api/v1/inventory", request);
    }

    @Override
    public List<ListingView> findListings(int itemInventoryId) {
        List<ListingView> result = new ArrayList<>();
        for (JsonNode draft : get("/api/v1/inventory/" + itemInventoryId + "/marketplace-listings")) {
            JsonNode listing = draft.path("marketplaceListing");
            JsonNode bricklink = draft.path("bricklinkMarketplaceListing");
            result.add(new ListingView(
                    listing.path("marketplaceListingId").asInt(),
                    integer(listing, "listingExternalServiceId"),
                    text(listing, "listingStatusCode"),
                    listing.hasNonNull("unitPrice") ? listing.get("unitPrice").decimalValue() : null,
                    listing.hasNonNull("fixedPrice") ? listing.get("fixedPrice").asBoolean() : null,
                    integer(bricklink, "bricklinkInventoryId"),
                    text(bricklink, "stockRoomId"),
                    text(bricklink, "environmentCode"),
                    text(bricklink, "lastRemoteSafetyStatusCode")
            ));
        }
        return result;
    }

    @Override
    public int createDraft(MarketplaceListingDraftCreateRequest request) {
        JsonNode response = post("/api/v1/marketplace-listings", request);
        return response.path("marketplaceListing").path("marketplaceListingId").asInt();
    }

    @Override
    public List<SyncRequestView> findSyncRequests(int marketplaceListingId) {
        List<SyncRequestView> result = new ArrayList<>();
        for (JsonNode node : get("/api/v1/marketplace-listings/" + marketplaceListingId + "/sync-requests")) {
            result.add(new SyncRequestView(
                    node.path("marketplaceListingSyncRequestId").asLong(),
                    text(node, "syncRequestTypeCode"),
                    text(node, "syncRequestStatusCode"),
                    dateTime(node, "createdAt"),
                    text(node, "lastErrorMessage")
            ));
        }
        return result;
    }

    private JsonNode get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(TIMEOUT).GET().build());
    }

    private JsonNode post(String path, Object body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the request for " + path, e);
        }
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SeedServiceException(request.method() + " " + request.uri() + " failed: " + e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SeedServiceException(request.method() + " " + request.uri() + " was interrupted", 0);
        }
        if (response.statusCode() / 100 != 2) {
            throw new SeedServiceException(request.method() + " " + request.uri() + " returned "
                    + response.statusCode() + ": " + response.body(), response.statusCode());
        }
        try {
            String body = response.body();
            return body == null || body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SeedServiceException(request.method() + " " + request.uri()
                    + " returned a body that is not JSON", response.statusCode());
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static ZonedDateTime dateTime(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(node.get(field).asText());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
