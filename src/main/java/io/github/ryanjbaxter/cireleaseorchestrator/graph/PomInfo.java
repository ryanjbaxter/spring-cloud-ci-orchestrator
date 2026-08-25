package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.util.Map;

/**
 * The subset of a pom.xml's own top-level structure that matters for dependency-graph derivation
 * and branch resolution: its own artifactId/version, its parent, and any {@code *.version}
 * properties (the convention Spring Cloud poms use to pin sibling-project versions).
 *
 * @param versionProperties keyed by project name (the {@code .version} suffix already stripped),
 *                           e.g. {@code spring-cloud-build.version} becomes {@code spring-cloud-build}
 */
public record PomInfo(
        String artifactId,
        String parentArtifactId,
        String ownVersion,
        String parentVersion,
        Map<String, String> versionProperties) {

    /**
     * The root pom's own version - present in most root poms, but some inherit it entirely from
     * their parent, in which case the parent's version is the effective one.
     */
    public String effectiveVersion() {
        return ownVersion != null ? ownVersion : parentVersion;
    }
}
