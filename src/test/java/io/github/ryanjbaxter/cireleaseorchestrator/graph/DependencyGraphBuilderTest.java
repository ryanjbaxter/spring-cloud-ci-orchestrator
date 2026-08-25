package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphBuilderTest {

    private final DependencyGraphBuilder builder = new DependencyGraphBuilder();

    @Test
    void parentArtifactIdCreatesAnEdgeFromParentToChild() {
        Set<String> inScope = Set.of("spring-cloud-build", "spring-cloud-commons");
        Map<String, List<PomInfo>> poms = Map.of(
                "spring-cloud-build", List.of(pom("spring-cloud-build-parent", null, Map.of())),
                "spring-cloud-commons", List.of(pom("spring-cloud-commons-parent", "spring-cloud-build", Map.of())));

        Map<String, Set<String>> edges = builder.build(inScope, poms);

        assertThat(edges.get("spring-cloud-build")).containsExactly("spring-cloud-commons");
        assertThat(edges.get("spring-cloud-commons")).isEmpty();
    }

    @Test
    void versionPropertyCreatesAnEdgeFromReferencedProjectToOwner() {
        Set<String> inScope = Set.of("spring-cloud-build", "spring-cloud-config");
        Map<String, List<PomInfo>> poms = Map.of(
                "spring-cloud-build", List.of(pom("spring-cloud-build-parent", null, Map.of())),
                "spring-cloud-config", List.of(pom("spring-cloud-config-server", null,
                        Map.of("spring-cloud-build", "5.0.3"))));

        Map<String, Set<String>> edges = builder.build(inScope, poms);

        assertThat(edges.get("spring-cloud-build")).containsExactly("spring-cloud-config");
    }

    @Test
    void referencesOutsideScopeAreIgnored() {
        Set<String> inScope = Set.of("spring-cloud-config");
        Map<String, List<PomInfo>> poms = Map.of(
                "spring-cloud-config", List.of(pom("spring-cloud-config-server", "spring-boot-starter-parent",
                        Map.of("some-external-thing", "1.0.0"))));

        Map<String, Set<String>> edges = builder.build(inScope, poms);

        assertThat(edges.get("spring-cloud-config")).isEmpty();
    }

    @Test
    void aProjectDoesNotDependOnItself() {
        Set<String> inScope = Set.of("spring-cloud-build");
        Map<String, List<PomInfo>> poms = Map.of(
                "spring-cloud-build", List.of(pom("spring-cloud-build-dependencies", "spring-cloud-build",
                        Map.of("spring-cloud-build", "5.0.3"))));

        Map<String, Set<String>> edges = builder.build(inScope, poms);

        assertThat(edges.get("spring-cloud-build")).isEmpty();
    }

    @Test
    void reverseInvertsAdjacency() {
        Map<String, Set<String>> edges = Map.of(
                "a", Set.of("b", "c"),
                "b", Set.of("c"),
                "c", Set.of());

        Map<String, Set<String>> reversed = DependencyGraphBuilder.reverse(edges);

        assertThat(reversed.get("c")).containsExactlyInAnyOrder("a", "b");
        assertThat(reversed.get("b")).containsExactly("a");
        assertThat(reversed.get("a")).isEmpty();
    }

    @Test
    void descendantsOfIncludesStartAndEverythingReachableForward() {
        // build -> commons -> {bus, config}; circuitbreaker is unrelated
        Map<String, Set<String>> edges = Map.of(
                "build", Set.of("commons"),
                "commons", Set.of("bus", "config"),
                "bus", Set.of(),
                "config", Set.of(),
                "circuitbreaker", Set.of());

        assertThat(DependencyGraphBuilder.descendantsOf("commons", edges))
                .containsExactlyInAnyOrder("commons", "bus", "config");
        assertThat(DependencyGraphBuilder.descendantsOf("build", edges))
                .containsExactlyInAnyOrder("build", "commons", "bus", "config");
        assertThat(DependencyGraphBuilder.descendantsOf("bus", edges)).containsExactly("bus");
    }

    @Test
    void restrictPrerequisitesToDropsPrerequisitesOutsideTheSubset() {
        // gateway depends on both commons and circuitbreaker
        Map<String, Set<String>> prerequisitesOf = Map.of(
                "gateway", Set.of("commons", "circuitbreaker"),
                "commons", Set.of("build"),
                "circuitbreaker", Set.of("build"),
                "build", Set.of());

        Map<String, Set<String>> restricted = DependencyGraphBuilder.restrictPrerequisitesTo(
                prerequisitesOf, Set.of("gateway", "commons"));

        // circuitbreaker isn't in the subset (assumed already handled), so it's dropped as a
        // blocker even though the unrestricted map lists it as a real prerequisite.
        assertThat(restricted.get("gateway")).containsExactly("commons");
        assertThat(restricted.get("commons")).isEmpty();
        assertThat(restricted).doesNotContainKey("circuitbreaker");
    }

    private PomInfo pom(String artifactId, String parentArtifactId, Map<String, String> versionProperties) {
        return new PomInfo(artifactId, parentArtifactId, null, null, versionProperties);
    }
}
