package com.vattima.lego.inventory.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;
import com.vattima.lego.inventory.seed.SeedManifest.SkippedItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the export query against an in-memory copy of the six legacy tables' relevant columns.
 */
class LegacyItemExporterTest {

    /** Legacy columns that describe a listing or lot rather than the item. Export must never read them. */
    private static final Set<String> LOT_COLUMNS = Set.of(
            "uuid", "inventory_id", "color_id", "color_name", "unit_price", "bind_id", "description", "remarks",
            "bulk", "is_retain", "is_stock_room", "stock_room_id", "date_created", "sale_rate", "tier_quantity1",
            "tier_quantity2", "tier_quantity3", "tier_price1", "tier_price2", "tier_price3", "my_weight",
            "order_id", "fixed_price", "for_sale", "internal_comments", "update_timestamp",
            "last_synchronized_timestamp", "extended_description", "extra_description");

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:legacy" + System.nanoTime() + ";MODE=MySQL");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE `condition` (condition_id INT, condition_code VARCHAR(5) NOT NULL)");
            statement.execute("INSERT INTO `condition` VALUES (1,'M'),(2,'E'),(3,'VG'),(4,'G')");
            statement.execute("CREATE TABLE item (item_id INT PRIMARY KEY, item_number VARCHAR(15), item_name VARCHAR(100))");
            statement.execute("CREATE TABLE bricklink_item (item_id INT, bl_item_number VARCHAR(10), bl_item_id INT)");
            statement.execute("CREATE TABLE bricklink_inventory ("
                    + "bl_inventory_id INT PRIMARY KEY, bl_item_number VARCHAR(10), item_type VARCHAR(20), "
                    + "quantity SMALLINT, new_or_used VARCHAR(1), completeness VARCHAR(1), sealed TINYINT, "
                    + "built_once TINYINT, my_cost DECIMAL(7,2), box_condition_id INT, instructions_condition_id INT, "
                    + "unit_price DECIMAL(7,2), remarks VARCHAR(1024), inventory_id INT, stock_room_id VARCHAR(1), "
                    + "order_id VARCHAR(11), description VARCHAR(1024), internal_comments VARCHAR(1024))");
            statement.execute("INSERT INTO item VALUES (10,'6365-1','Summer Cottage'),(11,'8880-1','Super Car'),"
                    + "(12,'10000-1','Sealed Thing'),(13,'5000-1','Gear')");
            statement.execute("INSERT INTO bricklink_item VALUES (10,'6365-1',1),(11,'8880-1',2),(12,'10000-1',3),(13,'5000-1',4)");
            // Every row carries lot data that must not reach the manifest.
            statement.execute("INSERT INTO bricklink_inventory VALUES "
                    + "(101,'6365-1','SET',1,'U','C',0,1,45.00,3,4, 59.99,'LOT REMARK',555001,'A',NULL,'html desc','secret note'),"
                    + "(102,'8880-1','SET',1,'U','C',0,NULL,NULL,NULL,4, 99.99,'LOT REMARK',555002,'A',NULL,'html desc','secret note'),"
                    + "(103,'10000-1','SET',1,'N','S',1,0,80.00,NULL,NULL, 149.99,'LOT REMARK',555003,'',NULL,'html desc','secret note'),"
                    + "(104,'6365-1','SET',1,'U','C',0,0,30.00,3,3, 49.99,'SOLD',555004,'A','ORDER-1','html desc','secret note'),"
                    + "(105,'5000-1','GEAR',1,'N','S',1,0,5.00,1,1, 9.99,'LOT REMARK',555005,'A',NULL,'html desc','secret note'),"
                    + "(106,'8880-1','SET',1,'N','C',0,0,70.00,1,1, 120.00,'LOT REMARK',555006,'A',NULL,'html desc','secret note')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void selectsUnsoldItemsAndClassifiesTheRest() throws Exception {
        SeedManifest manifest = export(new ExportFilter(null, null, null, 50));

        assertThat(manifest.items()).extracting(ManifestItem::sourceKey).containsExactly("101", "103", "106");
        assertThat(manifest.items()).extracting(ManifestItem::bucket)
                .containsExactly(SeedBucket.USED, SeedBucket.SEALED, SeedBucket.NEW);
        assertThat(manifest.skipped()).extracting(SkippedItem::sourceKey, SkippedItem::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("102", SkipReason.MISSING_GRADE),
                        org.assertj.core.groups.Tuple.tuple("105", SkipReason.NOT_A_SET));
        // 104 is sold (order_id set): it is neither selected nor reported.
        assertThat(manifest.items()).extracting(ManifestItem::sourceKey).doesNotContain("104");
        assertThat(manifest.items().get(0).itemName()).isEqualTo("Summer Cottage");
        assertThat(manifest.items().get(0).cost()).isEqualByComparingTo("45.00");
        assertThat(manifest.sourceSystem()).isEqualTo("LEGO_PROD_LEGACY");
    }

    @Test
    void bucketFilterAndLimitAreApplied() throws Exception {
        SeedManifest sealedOnly = export(new ExportFilter(Set.of(SeedBucket.SEALED), null, null, 50));
        assertThat(sealedOnly.items()).extracting(ManifestItem::sourceKey).containsExactly("103");
        assertThat(sealedOnly.skipped()).extracting(SkippedItem::reason).contains(SkipReason.BUCKET_NOT_SELECTED);

        SeedManifest limited = export(new ExportFilter(null, null, null, 2));
        assertThat(limited.items()).extracting(ManifestItem::sourceKey).containsExactly("101", "103");
        assertThat(limited.skipped()).extracting(SkippedItem::sourceKey, SkippedItem::reason)
                .contains(org.assertj.core.groups.Tuple.tuple("106", SkipReason.OVER_LIMIT));
    }

    @Test
    void explicitSourceKeysAndItemNumbersRestrictTheSelection() throws Exception {
        assertThat(export(new ExportFilter(null, List.of("106", "104"), null, 50)).items())
                .extracting(ManifestItem::sourceKey).containsExactly("106");
        assertThat(export(new ExportFilter(null, null, List.of("6365-1"), 50)).items())
                .extracting(ManifestItem::sourceKey).containsExactly("101");
    }

    @Test
    void queryNeverReadsALotColumn() {
        for (String column : LegacyItemExporter.SELECTED_COLUMNS) {
            String name = column.substring(column.indexOf('.') + 1).toLowerCase(Locale.ROOT);
            assertThat(LOT_COLUMNS).as("selected column %s", column).doesNotContain(name);
        }
        String selectList = LegacyItemExporter.BASE_QUERY.substring(0, LegacyItemExporter.BASE_QUERY.indexOf(" FROM "));
        for (String lotColumn : LOT_COLUMNS) {
            assertThat(selectList).as("select list").doesNotContainPattern("\\." + lotColumn + "\\b");
        }
        // order_id is only a filter.
        assertThat(LegacyItemExporter.BASE_QUERY).contains("WHERE bi.order_id IS NULL");
    }

    @Test
    void manifestJsonCarriesNoLotData() throws Exception {
        SeedManifest manifest = export(new ExportFilter(null, null, null, 50));
        String json = SeedCli.objectMapper().writeValueAsString(manifest);

        assertThat(json).doesNotContain("LOT REMARK", "555001", "59.99", "html desc", "secret note", "ORDER-1");
        Set<String> itemFields = new HashSet<>();
        JsonNode first = SeedCli.objectMapper().readTree(json).path("items").get(0);
        for (Iterator<String> names = first.fieldNames(); names.hasNext(); ) {
            itemFields.add(names.next());
        }
        assertThat(itemFields).containsExactlyInAnyOrder("sourceKey", "itemNumber", "itemName", "bucket",
                "newOrUsed", "completeness", "sealed", "builtOnce", "itemConditionCode", "boxConditionCode",
                "instructionsConditionCode", "cost");
    }

    @Test
    void manifestRoundTripsThroughJson() throws Exception {
        SeedManifest manifest = export(new ExportFilter(Set.of(SeedBucket.USED), null, null, 50));
        String json = SeedCli.objectMapper().writeValueAsString(manifest);

        SeedManifest read = SeedCli.objectMapper().readValue(json, SeedManifest.class);

        assertThat(read.items()).isEqualTo(manifest.items());
        assertThat(read.filter().buckets()).containsExactly(SeedBucket.USED);
    }

    @Test
    void connectionIsSetReadOnlyBeforeAnyQuery() throws Exception {
        // H2 ignores the read-only hint, so record the calls instead of asking the connection.
        List<String> calls = new java.util.ArrayList<>();
        Connection recording = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    calls.add(method.getName() + (args != null && args.length == 1 ? "(" + args[0] + ")" : ""));
                    return method.invoke(connection, args);
                });

        new LegacyItemExporter(CLOCK).export(recording, new ExportFilter(null, null, null, 50));

        assertThat(calls.get(0)).isEqualTo("setReadOnly(true)");
    }

    private SeedManifest export(ExportFilter filter) throws Exception {
        return new LegacyItemExporter(CLOCK).export(connection, filter);
    }
}
