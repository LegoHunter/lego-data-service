package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SeedItemMapperTest {

    @Test
    void usedCompleteGradedSetIsSeededWithConditionNa() {
        ManifestItem item = SeedItemMapper.map(row("SET", 1, "U", "C", false, "VG", "G")).item();

        assertThat(item.bucket()).isEqualTo(SeedBucket.USED);
        assertThat(item.itemConditionCode()).isEqualTo("NA");
        assertThat(item.boxConditionCode()).isEqualTo("VG");
        assertThat(item.instructionsConditionCode()).isEqualTo("G");
        assertThat(item.completeness()).isEqualTo("C");
        assertThat(item.sealed()).isFalse();
    }

    @Test
    void completenessSBecomesCompleteAndSealedWithDefaultGrades() {
        ManifestItem item = SeedItemMapper.map(row("SET", 1, "N", "S", false, null, null)).item();

        assertThat(item.bucket()).isEqualTo(SeedBucket.SEALED);
        assertThat(item.completeness()).isEqualTo("C");
        assertThat(item.sealed()).isTrue();
        assertThat(item.itemConditionCode()).isEqualTo("SL");
        assertThat(item.boxConditionCode()).isEqualTo("E");
        assertThat(item.instructionsConditionCode()).isEqualTo("M");
    }

    @Test
    void sealedItemKeepsGradesItHas() {
        ManifestItem item = SeedItemMapper.map(row("SET", 1, "N", "C", true, "VG", null)).item();

        assertThat(item.bucket()).isEqualTo(SeedBucket.SEALED);
        assertThat(item.boxConditionCode()).isEqualTo("VG");
        assertThat(item.instructionsConditionCode()).isEqualTo("M");
    }

    @Test
    void newUnsealedGradedSetIsNewBucket() {
        assertThat(SeedItemMapper.map(row("SET", 1, "N", "C", false, "M", "M")).item().bucket())
                .isEqualTo(SeedBucket.NEW);
    }

    @Test
    void unsealedItemsMissingAGradeAreSkipped() {
        assertThat(SeedItemMapper.map(row("SET", 1, "U", "C", false, null, "G")).skipReason())
                .isEqualTo(SkipReason.MISSING_GRADE);
        assertThat(SeedItemMapper.map(row("SET", 1, "N", "C", false, "M", null)).skipReason())
                .isEqualTo(SkipReason.MISSING_GRADE);
    }

    @Test
    void nonSetsQuantitiesAndCompletenessOutsideTheRulesAreSkipped() {
        assertThat(SeedItemMapper.map(row("GEAR", 1, "N", "S", true, "M", "M")).skipReason())
                .isEqualTo(SkipReason.NOT_A_SET);
        assertThat(SeedItemMapper.map(row("SET", 2, "N", "S", true, null, null)).skipReason())
                .isEqualTo(SkipReason.QUANTITY_NOT_ONE);
        assertThat(SeedItemMapper.map(row("SET", 1, "N", "X", false, "M", "M")).skipReason())
                .isEqualTo(SkipReason.UNSUPPORTED_COMPLETENESS);
        assertThat(SeedItemMapper.map(row("SET", 1, "Q", "C", false, "M", "M")).skipReason())
                .isEqualTo(SkipReason.UNSUPPORTED_NEW_OR_USED);
    }

    @Test
    void nullBuiltOnceAndCostDefault() {
        LegacyItemRow row = new LegacyItemRow("7", "6365-1", "Summer Cottage", "SET", 1, "U", "C", false,
                null, null, "VG", "VG");

        ManifestItem item = SeedItemMapper.map(row).item();

        assertThat(item.builtOnce()).isFalse();
        assertThat(item.cost()).isEqualByComparingTo("0.00");
    }

    private static LegacyItemRow row(String type, int quantity, String newOrUsed, String completeness, boolean sealed,
                                     String box, String instructions) {
        return new LegacyItemRow("1", "6365-1", "Summer Cottage", type, quantity, newOrUsed, completeness, sealed,
                Boolean.FALSE, new BigDecimal("12.50"), box, instructions);
    }
}
