package com.vattima.lego.inventory.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Marks an intake item as created by lower-environment seeding and records which source item it
 * was copied from. Only the seeding tool sends it; it is refused in PROD.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeedSourceRequest {
    /** For example {@code LEGO_PROD_LEGACY}. */
    @NotBlank
    @Size(max = 32)
    private String sourceSystem;

    /** The item key in the source system, for example the legacy {@code bl_inventory_id}. */
    @NotBlank
    @Size(max = 64)
    private String sourceKey;

    /** UUID of the seeding run. */
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            message = "Must be a UUID")
    private String seedRunId;
}
