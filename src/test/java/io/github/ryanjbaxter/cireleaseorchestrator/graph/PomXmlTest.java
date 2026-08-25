package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PomXmlTest {

    // Modeled on the real spring-cloud-commons/pom.xml: a parent pointing at spring-cloud-build,
    // and its own explicit <version>.
    @Test
    void parsesArtifactIdOwnVersionAndParent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                	<groupId>org.springframework.cloud</groupId>
                	<artifactId>spring-cloud-commons-parent</artifactId>
                	<version>5.0.3-SNAPSHOT</version>
                	<parent>
                		<groupId>org.springframework.cloud</groupId>
                		<artifactId>spring-cloud-build</artifactId>
                		<version>5.0.3-SNAPSHOT</version>
                	</parent>
                </project>
                """;

        PomInfo info = PomXml.parse(xml);

        assertThat(info.artifactId()).isEqualTo("spring-cloud-commons-parent");
        assertThat(info.ownVersion()).isEqualTo("5.0.3-SNAPSHOT");
        assertThat(info.parentArtifactId()).isEqualTo("spring-cloud-build");
        assertThat(info.parentVersion()).isEqualTo("5.0.3-SNAPSHOT");
        assertThat(info.effectiveVersion()).isEqualTo("5.0.3-SNAPSHOT");
    }

    @Test
    void parsesVersionProperties() {
        String xml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                	<artifactId>spring-cloud-config-server</artifactId>
                	<properties>
                		<spring-cloud-build.version>5.0.3</spring-cloud-build.version>
                		<spring-cloud-commons.version>5.0.3</spring-cloud-commons.version>
                		<unrelated.property>ignored</unrelated.property>
                	</properties>
                </project>
                """;

        PomInfo info = PomXml.parse(xml);

        assertThat(info.versionProperties())
                .containsEntry("spring-cloud-build", "5.0.3")
                .containsEntry("spring-cloud-commons", "5.0.3")
                .doesNotContainKey("unrelated");
    }

    @Test
    void doesNotConfuseNestedDependencyVersionsWithOwnVersion() {
        String xml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                	<artifactId>spring-cloud-bus</artifactId>
                	<version>5.0.3-SNAPSHOT</version>
                	<dependencies>
                		<dependency>
                			<groupId>org.springframework.cloud</groupId>
                			<artifactId>spring-cloud-commons</artifactId>
                			<version>99.99.99</version>
                		</dependency>
                	</dependencies>
                </project>
                """;

        PomInfo info = PomXml.parse(xml);

        assertThat(info.ownVersion()).isEqualTo("5.0.3-SNAPSHOT");
        assertThat(info.parentArtifactId()).isNull();
    }

    @Test
    void ownVersionIsNullWhenInheritedFromParent() {
        String xml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                	<artifactId>spring-cloud-config-client</artifactId>
                	<parent>
                		<artifactId>spring-cloud-config</artifactId>
                		<version>5.0.5-SNAPSHOT</version>
                	</parent>
                </project>
                """;

        PomInfo info = PomXml.parse(xml);

        assertThat(info.ownVersion()).isNull();
        assertThat(info.effectiveVersion()).isEqualTo("5.0.5-SNAPSHOT");
    }
}
