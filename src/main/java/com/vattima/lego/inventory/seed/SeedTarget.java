package com.vattima.lego.inventory.seed;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The environments a seeding run may write to. PROD is deliberately absent: there is no way to name
 * it on the command line. The service also refuses a seed source when it runs as PROD, as a
 * backstop for a {@code --base-url} that points somewhere unexpected.
 */
public enum SeedTarget {
    SANDBOX("sandbox", "https://lego-data-service.sandbox.internal.legohunter.io"),
    DEV("dev", "https://lego-data-service.dev.internal.legohunter.io");

    private final String code;
    private final String defaultBaseUrl;

    SeedTarget(String code, String defaultBaseUrl) {
        this.code = code;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String code() {
        return code;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public static SeedTarget parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--target is required: " + allowed());
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(target -> target.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Seeding never targets '" + value + "'. Allowed targets: " + allowed()));
    }

    private static String allowed() {
        return Arrays.stream(values()).map(SeedTarget::code).collect(Collectors.joining(", "));
    }
}
