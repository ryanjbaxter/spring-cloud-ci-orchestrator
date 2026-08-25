package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Kahn's algorithm, grouped into levels rather than a single flat order: every project in a level
 * has no unresolved prerequisite left once the previous levels have completed, so the levels
 * double as the orchestrator's concurrency batches (independent projects in the same level can be
 * triggered in parallel) and as a readable build-order printout.
 */
public class TopologicalSorter {

    public List<List<String>> sortIntoLevels(Map<String, Set<String>> edges) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String node : edges.keySet()) {
            inDegree.put(node, 0);
        }
        for (Set<String> dependents : edges.values()) {
            for (String dependent : dependents) {
                inDegree.merge(dependent, 1, Integer::sum);
            }
        }

        List<List<String>> levels = new ArrayList<>();
        List<String> current = new ArrayList<>(new TreeSet<>(nodesWithInDegreeZero(inDegree)));
        int processed = 0;

        while (!current.isEmpty()) {
            levels.add(current);
            processed += current.size();

            Set<String> next = new TreeSet<>();
            for (String node : current) {
                for (String dependent : edges.getOrDefault(node, Set.of())) {
                    if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                        next.add(dependent);
                    }
                }
            }
            current = new ArrayList<>(next);
        }

        if (processed != edges.size()) {
            Set<String> remaining = new TreeSet<>(edges.keySet());
            levels.forEach(remaining::removeAll);
            throw new IllegalStateException("Dependency graph has a cycle involving: " + remaining);
        }

        return levels;
    }

    private List<String> nodesWithInDegreeZero(Map<String, Integer> inDegree) {
        List<String> result = new ArrayList<>();
        inDegree.forEach((node, degree) -> {
            if (degree == 0) {
                result.add(node);
            }
        });
        return result;
    }

    /**
     * Drops every project not in {@code subset} from each level, and drops any level left empty
     * by that - used to resume a run scoped to just a failed project and its descendants while
     * keeping the original level grouping (and therefore concurrency) intact for what remains.
     */
    public static List<List<String>> pruneToSubset(List<List<String>> levels, Set<String> subset) {
        List<List<String>> pruned = new ArrayList<>();
        for (List<String> level : levels) {
            List<String> kept = level.stream().filter(subset::contains).toList();
            if (!kept.isEmpty()) {
                pruned.add(kept);
            }
        }
        return pruned;
    }
}
