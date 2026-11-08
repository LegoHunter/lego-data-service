package com.vattima.lego.inventory.seed;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Target guard, argument parsing and option-file parsing.
 */
class SeedCliSupportTest {

    @Test
    void onlySandboxAndDevAreTargets() {
        assertThat(SeedTarget.parse("sandbox")).isEqualTo(SeedTarget.SANDBOX);
        assertThat(SeedTarget.parse("DEV")).isEqualTo(SeedTarget.DEV);
        assertThatThrownBy(() -> SeedTarget.parse("prod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never targets 'prod'");
        assertThatThrownBy(() -> SeedTarget.parse("local")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeedTarget.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importRefusesAProdTargetBeforeReadingAnything() {
        SeedCli cli = new SeedCli(new PrintStream(new ByteArrayOutputStream()), Clock.systemUTC());

        assertThatThrownBy(() -> cli.run(new String[]{"import", "--target=prod", "--manifest=does-not-exist.json"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never targets");
    }

    @Test
    void argumentsAreParsedAndUnknownOptionsRejected() {
        CliArguments arguments = CliArguments.parse(new String[]{"export", "--limit=10", "--buckets=USED, SEALED", "--dry-run"});

        assertThat(arguments.command()).isEqualTo("export");
        assertThat(arguments.integer("limit", 50)).isEqualTo(10);
        assertThat(arguments.list("buckets")).containsExactly("USED", "SEALED");
        assertThat(arguments.flag("dry-run")).isTrue();
        assertThatThrownBy(() -> arguments.allowOnly(Set.of("limit", "buckets")))
                .hasMessageContaining("--dry-run");
        assertThatThrownBy(() -> CliArguments.parse(new String[]{"export", "--limit=1", "--limit=2"}))
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> CliArguments.parse(new String[]{"export", "stray"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionFileReadsClientSectionOnlyAndKeepsThePasswordOutOfTheUrl() {
        MySqlOptionFile file = MySqlOptionFile.parse(List.of(
                "# comment",
                "[mysqld]",
                "port=9999",
                "[client]",
                "host = 10.0.2.51",
                "user=\"reader\"",
                "password='s3cret'",
                "[mysqldump]",
                "user=other"));

        assertThat(file.user()).isEqualTo("reader");
        assertThat(file.jdbcUrl("lego_prod_legacy"))
                .isEqualTo("jdbc:mysql://10.0.2.51:3306/lego_prod_legacy?connectionTimeZone=UTC")
                .doesNotContain("s3cret");
    }
}
