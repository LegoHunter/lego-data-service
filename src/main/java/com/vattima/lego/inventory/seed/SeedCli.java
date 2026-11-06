package com.vattima.lego.inventory.seed;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;
import com.vattima.lego.inventory.seed.SeedManifest.SkippedItem;

import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HOMELAB-40 lower-environment seeding CLI. It runs on a workstation, never in the cluster.
 *
 * <pre>
 * export --legacy-defaults-file=PATH --out=manifest.json
 *        [--buckets=USED,SEALED,NEW] [--source-keys=1,2] [--item-numbers=6365-1,...] [--limit=50]
 *        [--legacy-database=lego_prod_legacy]
 * import --target=sandbox|dev --manifest=manifest.json
 *        [--dry-run] [--max-per-run=50] [--environment-ceiling=200] [--seed-run-id=UUID] [--base-url=URL]
 * status --target=sandbox|dev [--seed-run-id=UUID] [--base-url=URL]
 * </pre>
 *
 * <p>Launch it from the service jar with Spring Boot's PropertiesLauncher:
 * {@code java -Dloader.main=com.vattima.lego.inventory.seed.SeedCli -cp lego-data-service.jar
 * org.springframework.boot.loader.launch.PropertiesLauncher import ...}. It starts no Spring
 * context, so the service's database, JMS and web configuration never load.
 */
public final class SeedCli {

    static final String DEFAULT_LEGACY_DATABASE = "lego_prod_legacy";

    private final PrintStream out;
    private final Clock clock;

    SeedCli(PrintStream out, Clock clock) {
        this.out = out;
        this.clock = clock;
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = new SeedCli(System.out, Clock.systemUTC()).run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Usage error: " + e.getMessage());
            exitCode = 2;
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    int run(String[] args) throws Exception {
        CliArguments arguments = CliArguments.parse(args);
        return switch (arguments.command()) {
            case "export" -> export(arguments);
            case "import" -> importManifest(arguments);
            case "status" -> status(arguments);
            default -> throw new IllegalArgumentException("Unknown command '" + arguments.command()
                    + "': use export, import or status");
        };
    }

    private int export(CliArguments arguments) throws Exception {
        arguments.allowOnly(Set.of("legacy-defaults-file", "legacy-database", "buckets", "source-keys",
                "item-numbers", "limit", "out"));
        Path optionFile = Path.of(arguments.required("legacy-defaults-file"));
        Path outFile = Path.of(arguments.required("out"));
        if (Files.exists(outFile)) {
            throw new IllegalArgumentException(outFile + " already exists; choose a new --out");
        }
        ExportFilter filter = new ExportFilter(
                buckets(arguments),
                arguments.list("source-keys"),
                arguments.list("item-numbers"),
                arguments.integer("limit", SeedImporter.MAX_ITEMS_PER_RUN)
        );
        String database = arguments.optional("legacy-database") == null
                ? DEFAULT_LEGACY_DATABASE : arguments.optional("legacy-database");

        MySqlOptionFile options = MySqlOptionFile.read(optionFile);
        out.println("Reading " + options.jdbcUrl(database) + " as " + options.user() + " (read-only)");
        SeedManifest manifest;
        try (Connection connection = options.connect(database)) {
            manifest = new LegacyItemExporter(clock).export(connection, filter);
        }
        objectMapper().writeValue(outFile.toFile(), manifest);

        out.println("Wrote " + outFile + ": " + manifest.items().size() + " seedable items.");
        countBy(manifest.items().stream().map(ManifestItem::bucket).map(Enum::name).toList())
                .forEach((bucket, count) -> out.println("  " + bucket + ": " + count));
        out.println("Skipped " + manifest.skipped().size() + " unsold items:");
        countBy(manifest.skipped().stream().map(SkippedItem::reason).map(Enum::name).toList())
                .forEach((reason, count) -> out.println("  " + reason + ": " + count));
        return 0;
    }

    private int importManifest(CliArguments arguments) throws Exception {
        arguments.allowOnly(Set.of("target", "manifest", "dry-run", "max-per-run", "environment-ceiling",
                "seed-run-id", "base-url"));
        SeedTarget target = SeedTarget.parse(arguments.required("target"));
        SeedManifest manifest = objectMapper().readValue(Path.of(arguments.required("manifest")).toFile(),
                SeedManifest.class);
        String seedRunId = arguments.optional("seed-run-id") == null
                ? UUID.randomUUID().toString() : UUID.fromString(arguments.optional("seed-run-id")).toString();
        SeedImporter.Options options = new SeedImporter.Options(
                arguments.integer("max-per-run", SeedImporter.MAX_ITEMS_PER_RUN),
                arguments.integer("environment-ceiling", SeedImporter.DEFAULT_ENVIRONMENT_CEILING),
                arguments.flag("dry-run")
        );
        String baseUrl = baseUrl(arguments, target);
        out.println((options.dryRun() ? "DRY RUN: " : "") + "Seeding " + target.code() + " (" + baseUrl
                + ") from " + manifest.items().size() + " manifest items, seed run " + seedRunId);

        SeedImporter.Report report = new SeedImporter(client(baseUrl), clock)
                .run(manifest, seedRunId, target.code(), options);

        out.printf(Locale.ROOT, "%-10s %-12s %-8s %-8s %s%n", "source", "item", "itemId", "listing", "outcome");
        for (SeedImporter.Row row : report.rows()) {
            out.printf(Locale.ROOT, "%-10s %-12s %-8s %-8s %s%n", row.sourceKey(), row.itemNumber(),
                    blank(row.itemInventoryId()), blank(row.marketplaceListingId()), row.outcome());
        }
        countBy(report.rows().stream().map(row -> row.outcome().name()).toList())
                .forEach((outcome, count) -> out.println(outcome + ": " + count));
        if (!options.dryRun()) {
            out.println("The pricing plane prices each draft and creates its lot. Follow progress with:");
            out.println("  status --target=" + target.code() + " --seed-run-id=" + seedRunId);
        }
        return 0;
    }

    private int status(CliArguments arguments) {
        arguments.allowOnly(Set.of("target", "seed-run-id", "base-url"));
        SeedTarget target = SeedTarget.parse(arguments.required("target"));
        String seedRunId = arguments.optional("seed-run-id");
        SeedStatusReporter.Report report = new SeedStatusReporter(client(baseUrl(arguments, target))).report(seedRunId);

        out.printf(Locale.ROOT, "%-8s %-7s %-8s %-7s %-9s %-14s %-11s %-3s %-9s %-14s %s%n", "source", "item",
                "listing", "status", "price", "create", "lot", "SR", "env", "safety", "stage");
        for (SeedStatusReporter.Row row : report.rows()) {
            out.printf(Locale.ROOT, "%-8s %-7s %-8s %-7s %-9s %-14s %-11s %-3s %-9s %-14s %s%n",
                    row.sourceKey(), row.itemInventoryId(), blank(row.marketplaceListingId()),
                    blank(row.listingStatusCode()), blank(row.unitPrice()),
                    row.listingCreateSyncRequestId() == null ? ""
                            : row.listingCreateSyncRequestId() + ":" + row.listingCreateStatusCode(),
                    blank(row.bricklinkInventoryId()), blank(row.stockRoomId()), blank(row.environmentCode()),
                    blank(row.safetyStatusCode()), row.stage());
        }
        out.println(report.rows().size() + " seeded items" + (seedRunId == null ? "" : " in run " + seedRunId) + ":");
        report.counts().forEach((stage, count) -> {
            if (count > 0) {
                out.println("  " + stage + ": " + count);
            }
        });
        return 0;
    }

    private static Set<SeedBucket> buckets(CliArguments arguments) {
        Set<SeedBucket> buckets = EnumSet.noneOf(SeedBucket.class);
        for (String name : arguments.list("buckets")) {
            try {
                buckets.add(SeedBucket.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown bucket '" + name + "': use USED, SEALED or NEW");
            }
        }
        return buckets;
    }

    private static String baseUrl(CliArguments arguments, SeedTarget target) {
        String override = arguments.optional("base-url");
        return override == null ? target.defaultBaseUrl() : override;
    }

    private static SeedServiceClient client(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        return new HttpSeedServiceClient(httpClient, objectMapper(), baseUrl);
    }

    static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static Map<String, Long> countBy(java.util.List<String> values) {
        return values.stream().collect(Collectors.groupingBy(value -> value, TreeMap::new, Collectors.counting()));
    }

    private static String blank(Object value) {
        return value == null ? "" : value.toString();
    }
}
