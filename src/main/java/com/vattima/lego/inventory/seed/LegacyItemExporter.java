package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;
import com.vattima.lego.inventory.seed.SeedManifest.SkippedItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.legohunter.data.dto.ItemInventorySeedSource.SOURCE_SYSTEM_LEGO_PROD_LEGACY;

/**
 * Reads unsold items from legacy PROD ({@code lego_prod_legacy}) and builds a {@link SeedManifest}.
 *
 * <p>It runs on a read-only connection (the caller's {@code claude_legacy_ro}-style identity) and
 * selects item columns only. Listing and lot columns are never read: see
 * {@link #SELECTED_COLUMNS}. {@code order_id} appears only in the WHERE clause, to keep sold items
 * out.
 */
public class LegacyItemExporter {

    /**
     * The only columns the export reads. Every one is item or catalog data.
     */
    static final List<String> SELECTED_COLUMNS = List.of(
            "bi.bl_inventory_id",
            "bi.bl_item_number",
            "bi.item_type",
            "bi.quantity",
            "bi.new_or_used",
            "bi.completeness",
            "bi.sealed",
            "bi.built_once",
            "bi.my_cost",
            "bc.condition_code",
            "ic.condition_code",
            "i.item_name"
    );

    static final String BASE_QUERY = "SELECT "
            + "bi.bl_inventory_id AS source_key, "
            + "bi.bl_item_number AS item_number, "
            + "bi.item_type AS item_type, "
            + "bi.quantity AS quantity, "
            + "bi.new_or_used AS new_or_used, "
            + "bi.completeness AS completeness, "
            + "bi.sealed AS sealed, "
            + "bi.built_once AS built_once, "
            + "bi.my_cost AS cost, "
            + "bc.condition_code AS box_condition_code, "
            + "ic.condition_code AS instructions_condition_code, "
            + "i.item_name AS item_name "
            + "FROM bricklink_inventory bi "
            + "LEFT JOIN `condition` bc ON bc.condition_id = bi.box_condition_id "
            + "LEFT JOIN `condition` ic ON ic.condition_id = bi.instructions_condition_id "
            + "LEFT JOIN bricklink_item b ON b.bl_item_number = bi.bl_item_number "
            + "LEFT JOIN item i ON i.item_id = b.item_id "
            + "WHERE bi.order_id IS NULL";

    private final Clock clock;

    public LegacyItemExporter(Clock clock) {
        this.clock = clock;
    }

    public SeedManifest export(Connection connection, ExportFilter filter) throws SQLException {
        connection.setReadOnly(true);
        List<LegacyItemRow> rows = read(connection, filter);

        List<ManifestItem> items = new ArrayList<>();
        List<SkippedItem> skipped = new ArrayList<>();
        for (LegacyItemRow row : rows) {
            SeedItemMapper.Result result = SeedItemMapper.map(row);
            if (!result.seedable()) {
                skipped.add(new SkippedItem(row.sourceKey(), row.itemNumber(), result.skipReason()));
            } else if (!filter.buckets().contains(result.item().bucket())) {
                skipped.add(new SkippedItem(row.sourceKey(), row.itemNumber(), SkipReason.BUCKET_NOT_SELECTED));
            } else if (items.size() >= filter.limit()) {
                skipped.add(new SkippedItem(row.sourceKey(), row.itemNumber(), SkipReason.OVER_LIMIT));
            } else {
                items.add(result.item());
            }
        }
        return new SeedManifest(
                SOURCE_SYSTEM_LEGO_PROD_LEGACY,
                ZonedDateTime.now(clock),
                filter,
                Collections.unmodifiableList(items),
                Collections.unmodifiableList(skipped)
        );
    }

    static String query(ExportFilter filter) {
        StringBuilder sql = new StringBuilder(BASE_QUERY);
        if (!filter.sourceKeys().isEmpty()) {
            sql.append(" AND bi.bl_inventory_id IN (").append(placeholders(filter.sourceKeys().size())).append(')');
        }
        if (!filter.itemNumbers().isEmpty()) {
            sql.append(" AND bi.bl_item_number IN (").append(placeholders(filter.itemNumbers().size())).append(')');
        }
        return sql.append(" ORDER BY bi.bl_inventory_id").toString();
    }

    private List<LegacyItemRow> read(Connection connection, ExportFilter filter) throws SQLException {
        // A catalog number can map to more than one legacy item row; keep the first per source key.
        Map<String, LegacyItemRow> rows = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(query(filter))) {
            int index = 1;
            for (String sourceKey : filter.sourceKeys()) {
                statement.setInt(index++, Integer.parseInt(sourceKey.trim()));
            }
            for (String itemNumber : filter.itemNumbers()) {
                statement.setString(index++, itemNumber.trim());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LegacyItemRow row = toRow(resultSet);
                    rows.putIfAbsent(row.sourceKey(), row);
                }
            }
        }
        return new ArrayList<>(rows.values());
    }

    private static LegacyItemRow toRow(ResultSet resultSet) throws SQLException {
        boolean builtOnce = resultSet.getBoolean("built_once");
        Boolean builtOnceOrNull = resultSet.wasNull() ? null : builtOnce;
        return new LegacyItemRow(
                String.valueOf(resultSet.getInt("source_key")),
                resultSet.getString("item_number"),
                resultSet.getString("item_name"),
                resultSet.getString("item_type"),
                resultSet.getInt("quantity"),
                resultSet.getString("new_or_used"),
                resultSet.getString("completeness"),
                resultSet.getBoolean("sealed"),
                builtOnceOrNull,
                resultSet.getBigDecimal("cost"),
                resultSet.getString("box_condition_code"),
                resultSet.getString("instructions_condition_code")
        );
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
