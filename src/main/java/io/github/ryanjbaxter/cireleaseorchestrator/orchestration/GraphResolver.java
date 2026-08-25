package io.github.ryanjbaxter.cireleaseorchestrator.orchestration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.github.ryanjbaxter.cireleaseorchestrator.branch.BranchResolution;
import io.github.ryanjbaxter.cireleaseorchestrator.branch.BranchResolver;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.DependencyGraphBuilder;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomFetcher;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomInfo;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.ProjectNode;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.TopologicalSorter;

/**
 * Resolves "which projects, in what order" - the one piece of work every action this app can take
 * (trigger CI, trigger docs deploys, anything added later) needs the same answer to. Resolves each
 * requested project's repo/branch via {@link BranchResolver} against whatever branch actually
 * carries that release train's real versions, fetches that branch's poms, and derives the
 * dependency-ordered levels from them.
 *
 * <p>The branch resolved here is deliberately the real development branch, not necessarily the
 * branch a caller ultimately dispatches work against. A project's {@code docs-build} branch, for
 * example, carries a single-module, dependency-free stand-in pom (artifactId
 * {@code <project>-docs-build}, whose only cross-reference is a {@code <parent>} pointing back at
 * its own project) - deriving the graph from those poms would produce no edges at all. The real
 * multi-module pom on the resolved release-train branch is what actually encodes the dependency
 * order, so that's what this always uses, regardless of where the caller ends up dispatching to.
 */
public class GraphResolver {

    public record Resolved(
            Map<String, ProjectNode> nodes,
            Map<String, String> branchFailures,
            Map<String, Set<String>> edges,
            Map<String, Set<String>> prerequisitesOf,
            List<List<String>> levels) {
    }

    private final BranchResolver branchResolver;
    private final PomFetcher pomFetcher;
    private final DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder();
    private final TopologicalSorter sorter = new TopologicalSorter();

    public GraphResolver(BranchResolver branchResolver, PomFetcher pomFetcher) {
        this.branchResolver = branchResolver;
        this.pomFetcher = pomFetcher;
    }

    public Resolved resolve(GitHub gitHub, Map<String, String> versions, Set<String> requested, boolean commercial)
            throws IOException {
        Map<String, ProjectNode> nodes = new LinkedHashMap<>();
        Map<String, String> branchFailures = new LinkedHashMap<>();

        for (String project : requested) {
            String repoFullName = commercial ? "spring-cloud/" + project + "-commercial" : "spring-cloud/" + project;
            GHRepository repo;
            try {
                repo = gitHub.getRepository(repoFullName);
            } catch (GHFileNotFoundException e) {
                branchFailures.put(project, "repository not found or not accessible: " + repoFullName);
                continue;
            }

            BranchResolution resolution = branchResolver.resolve(repo, versions.get(project), commercial);
            switch (resolution) {
                case BranchResolution.Resolved resolved ->
                        nodes.put(project, new ProjectNode(project, repoFullName, resolved.branch(), versions.get(project)));
                case BranchResolution.Failed failed -> branchFailures.put(project, failed.message());
            }
        }

        Map<String, List<PomInfo>> pomsByProject = new LinkedHashMap<>();
        for (ProjectNode node : nodes.values()) {
            GHRepository repo = gitHub.getRepository(node.repo());
            pomsByProject.put(node.name(), pomFetcher.fetchAllPoms(repo, node.branch()));
        }

        Map<String, Set<String>> edges = graphBuilder.build(nodes.keySet(), pomsByProject);
        Map<String, Set<String>> prerequisitesOf = DependencyGraphBuilder.reverse(edges);
        List<List<String>> levels = sorter.sortIntoLevels(edges);

        return new Resolved(nodes, branchFailures, edges, prerequisitesOf, levels);
    }
}
