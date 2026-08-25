package io.github.ryanjbaxter.cireleaseorchestrator.releaser;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaserConfigClientTest {

    private final ReleaserConfigClient client = new ReleaserConfigClient();

    @Test
    void fileNameForPlainVersion() {
        assertThat(client.fileNameFor("2025.1.3")).isEqualTo("2025_1_3.properties");
    }

    @Test
    void fileNameForSnapshotQualifierIsLowercased() {
        assertThat(client.fileNameFor("2025.1.3-SNAPSHOT")).isEqualTo("2025_1_3-snapshot.properties");
    }

    @Test
    void fileNameForFourSegmentVersion() {
        assertThat(client.fileNameFor("2025.1.2.1")).isEqualTo("2025_1_2_1.properties");
    }

    @Test
    void fileNameForRcQualifier() {
        assertThat(client.fileNameFor("2025.1.0-RC1")).isEqualTo("2025_1_0-rc1.properties");
    }

    @Test
    void fileNameForHyphenatedTwoWordQualifier() {
        assertThat(client.fileNameFor("2025.1.3-INTERNAL-SNAPSHOT"))
                .isEqualTo("2025_1_3-internal-snapshot.properties");
    }

    @Test
    void fileNameForRejectsMalformedVersion() {
        assertThatThrownBy(() -> client.fileNameFor("not-a-version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseExtractsFixedVersionEntries() {
        String content = """
                releaser.fixed-versions[spring-boot]=4.0.8
                releaser.fixed-versions[spring-cloud-build]=5.0.3
                releaser.fixed-versions[spring-cloud-config]=5.0.5
                releaser.fixed-versions[spring-cloud-release]=2025.1.3
                """;

        Map<String, String> versions = client.parse(content);

        assertThat(versions)
                .containsEntry("spring-boot", "4.0.8")
                .containsEntry("spring-cloud-build", "5.0.3")
                .containsEntry("spring-cloud-config", "5.0.5")
                .containsEntry("spring-cloud-release", "2025.1.3")
                .hasSize(4);
    }

    @Test
    void parseIgnoresUnrelatedLines() {
        String content = """
                # a comment
                some.other.property=value

                releaser.fixed-versions[spring-cloud-build]=5.0.3
                """;

        assertThat(client.parse(content)).containsExactly(Map.entry("spring-cloud-build", "5.0.3"));
    }

    @Test
    void parseHandlesCrlfLineEndings() {
        String content = "releaser.fixed-versions[spring-cloud-build]=5.0.3\r\n"
                + "releaser.fixed-versions[spring-cloud-config]=5.0.5\r\n";

        assertThat(client.parse(content))
                .containsEntry("spring-cloud-build", "5.0.3")
                .containsEntry("spring-cloud-config", "5.0.5");
    }
}
