package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopologicalSorterTest {

    private final TopologicalSorter sorter = new TopologicalSorter();

    @Test
    void singleChainProducesOneNodePerLevel() {
        Map<String, Set<String>> edges = Map.of(
                "build", Set.of("commons"),
                "commons", Set.of("config"),
                "config", Set.of());

        List<List<String>> levels = sorter.sortIntoLevels(edges);

        assertThat(levels).containsExactly(List.of("build"), List.of("commons"), List.of("config"));
    }

    @Test
    void independentBranchesShareALevel() {
        // build -> commons -> {bus, config} (bus and config both only depend on commons)
        Map<String, Set<String>> edges = Map.of(
                "build", Set.of("commons"),
                "commons", Set.of("bus", "config"),
                "bus", Set.of(),
                "config", Set.of());

        List<List<String>> levels = sorter.sortIntoLevels(edges);

        assertThat(levels).containsExactly(
                List.of("build"),
                List.of("commons"),
                List.of("bus", "config"));
    }

    @Test
    void nodeWaitsForAllOfItsPrerequisites() {
        // gateway depends on both commons and circuitbreaker
        Map<String, Set<String>> edges = Map.of(
                "commons", Set.of("gateway"),
                "circuitbreaker", Set.of("gateway"),
                "gateway", Set.of());

        List<List<String>> levels = sorter.sortIntoLevels(edges);

        assertThat(levels).containsExactly(List.of("circuitbreaker", "commons"), List.of("gateway"));
    }

    @Test
    void cycleThrows() {
        Map<String, Set<String>> edges = Map.of(
                "a", Set.of("b"),
                "b", Set.of("a"));

        assertThatThrownBy(() -> sorter.sortIntoLevels(edges))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void emptyGraphProducesNoLevels() {
        assertThat(sorter.sortIntoLevels(Map.of())).isEmpty();
    }

    @Test
    void pruneToSubsetDropsProjectsAndEmptiedLevels() {
        List<List<String>> levels = List.of(
                List.of("build"),
                List.of("commons"),
                List.of("bus", "config"));

        List<List<String>> pruned = TopologicalSorter.pruneToSubset(levels, Set.of("commons", "bus"));

        // "build" was the only entry in its level, so that whole level disappears once pruned.
        assertThat(pruned).containsExactly(List.of("commons"), List.of("bus"));
    }

    @Test
    void pruneToSubsetKeepingEverythingIsANoOp() {
        List<List<String>> levels = List.of(List.of("build"), List.of("commons"));

        assertThat(TopologicalSorter.pruneToSubset(levels, Set.of("build", "commons"))).isEqualTo(levels);
    }
}
