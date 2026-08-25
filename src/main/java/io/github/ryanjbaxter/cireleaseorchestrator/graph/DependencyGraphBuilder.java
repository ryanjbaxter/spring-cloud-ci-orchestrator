package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the dependency graph from each in-scope project's parsed poms, the same way
 * update-project-versions/src/index.js in spring-cloud-github-actions identifies inter-project
 * references: a pom's {@code <parent>} artifactId, and any {@code *.version} property, that
 * matches another in-scope project name.
 */
public class DependencyGraphBuilder {

    /**
     * @return adjacency from a project to the projects that depend on it (i.e. must be built
     *         after it) - the shape {@link TopologicalSorter} expects.
     */
    public Map<String, Set<String>> build(Set<String> inScopeProjects, Map<String, List<PomInfo>> pomsByProject) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (String project : inScopeProjects) {
            edges.put(project, new LinkedHashSet<>());
        }

        for (Map.Entry<String, List<PomInfo>> entry : pomsByProject.entrySet()) {
            String project = entry.getKey();
            if (!inScopeProjects.contains(project)) {
                continue;
            }
            for (PomInfo pom : entry.getValue()) {
                addEdgeIfInScope(edges, inScopeProjects, ArtifactNames.toProjectName(pom.parentArtifactId()), project);
                for (String referencedProject : pom.versionProperties().keySet()) {
                    addEdgeIfInScope(edges, inScopeProjects, referencedProject, project);
                }
            }
        }
        return edges;
    }

    private void addEdgeIfInScope(Map<String, Set<String>> edges, Set<String> inScopeProjects,
            String prerequisite, String dependent) {
        if (prerequisite == null || prerequisite.equals(dependent) || !inScopeProjects.contains(prerequisite)) {
            return;
        }
        edges.get(prerequisite).add(dependent);
    }

    /**
     * Reverses a prerequisite-&gt;dependents adjacency into dependent-&gt;prerequisites, which is
     * what the coordinator needs to decide whether a project's prerequisites all succeeded.
     */
    public static Map<String, Set<String>> reverse(Map<String, Set<String>> edges) {
        Map<String, Set<String>> reversed = new LinkedHashMap<>();
        for (String node : edges.keySet()) {
            reversed.put(node, new LinkedHashSet<>());
        }
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            for (String dependent : entry.getValue()) {
                reversed.computeIfAbsent(dependent, k -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        return reversed;
    }

    /**
     * The set of projects a resumed run needs to (re)trigger: {@code start} itself plus every
     * project reachable by following prerequisite-&gt;dependents edges forward from it - i.e.
     * everything downstream that could be affected by rebuilding {@code start}. Used to resume
     * after a mid-run failure without restarting the whole graph or persisting any state: GitHub's
     * API is re-queried for a fresh graph each run, and this just prunes it to the affected subset.
     */
    public static Set<String> descendantsOf(String start, Map<String, Set<String>> edges) {
        Set<String> closure = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        closure.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (String dependent : edges.getOrDefault(queue.poll(), Set.of())) {
                if (closure.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        return closure;
    }

    /**
     * Restricts a dependent-&gt;prerequisites map to a subset of projects, dropping any
     * prerequisite outside the subset. Used together with {@link #descendantsOf} when resuming: a
     * project at the edge of the resumed subset may have a prerequisite that isn't being rebuilt
     * this run (it's assumed to already be in a good state), and that prerequisite must not block it.
     */
    public static Map<String, Set<String>> restrictPrerequisitesTo(Map<String, Set<String>> prerequisitesOf,
            Set<String> subset) {
        Map<String, Set<String>> restricted = new LinkedHashMap<>();
        for (String project : subset) {
            Set<String> kept = new LinkedHashSet<>(prerequisitesOf.getOrDefault(project, Set.of()));
            kept.retainAll(subset);
            restricted.put(project, kept);
        }
        return restricted;
    }
}
