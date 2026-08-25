package io.github.ryanjbaxter.cireleaseorchestrator.orchestration;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ryanjbaxter.cireleaseorchestrator.ci.BuildOutcome;
import io.github.ryanjbaxter.cireleaseorchestrator.ci.OrchestrationCoordinator;
import io.github.ryanjbaxter.cireleaseorchestrator.ci.WorkflowTrigger;
import io.github.ryanjbaxter.cireleaseorchestrator.cli.OrchestratorArgs;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.DependencyGraphBuilder;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.ProjectNode;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.TopologicalSorter;

/**
 * Everything downstream of {@link GraphResolver}: apply an optional {@code --resume-from} prune,
 * print the plan, and (unless {@code --dry-run}) trigger and wait via {@link OrchestrationCoordinator}
 * - shared by every action this app takes, which differ only in which branch each project's
 * workflow is actually dispatched against and which workflow file(s) to look for there.
 */
public class OrchestrationCommand {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationCommand.class);

    private final GraphResolver graphResolver;

    public OrchestrationCommand(GraphResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    /**
     * @param dispatchBranch how to derive the branch to actually dispatch a project's workflow
     *                       against, from that project's resolved graph node - {@code ProjectNode::branch}
     *                       to dispatch against the same branch the graph was derived from (CI), or
     *                       a constant like {@code node -> "docs-build"} to dispatch elsewhere while
     *                       still using the real graph for ordering (docs deploys).
     */
    public void run(GitHub gitHub, String token, OrchestratorArgs args, Map<String, String> versions,
            Set<String> requested, boolean commercial, Function<ProjectNode, String> dispatchBranch,
            List<String> workflowCandidates) throws IOException {

        GraphResolver.Resolved resolved = graphResolver.resolve(gitHub, versions, requested, commercial);
        Map<String, ProjectNode> nodes = resolved.nodes();
        Map<String, String> branchFailures = resolved.branchFailures();
        Map<String, Set<String>> edges = resolved.edges();
        Map<String, Set<String>> prerequisitesOf = resolved.prerequisitesOf();
        List<List<String>> levels = resolved.levels();

        log.info("Resolved {} of {} project(s); {} skipped due to branch resolution.",
                nodes.size(), requested.size(), branchFailures.size());
        branchFailures.forEach((project, message) -> log.warn("  {} -> {}", project, message));

        if (args.resumeFrom() != null) {
            String resumeFrom = args.resumeFrom();
            if (!nodes.containsKey(resumeFrom)) {
                throw new IllegalArgumentException("--resume-from '" + resumeFrom + "' is not a buildable project "
                        + "in this run (check spelling, --projects, and the branch-resolution skips above). "
                        + "In scope: " + new TreeSet<>(nodes.keySet()));
            }

            Set<String> closure = DependencyGraphBuilder.descendantsOf(resumeFrom, edges);
            Set<String> excluded = new TreeSet<>(nodes.keySet());
            excluded.removeAll(closure);

            levels = TopologicalSorter.pruneToSubset(levels, closure);
            prerequisitesOf = DependencyGraphBuilder.restrictPrerequisitesTo(prerequisitesOf, closure);

            log.info("Resuming from {}: {} project(s) will (re)build, {} excluded as already handled or unrelated: {}",
                    resumeFrom, closure.size(), excluded.size(), excluded);
        }

        Map<String, ProjectNode> dispatchNodes = new LinkedHashMap<>();
        for (ProjectNode node : nodes.values()) {
            dispatchNodes.put(node.name(), new ProjectNode(node.name(), node.repo(), dispatchBranch.apply(node), node.version()));
        }

        printPlan(dispatchNodes, levels);

        if (args.dryRun()) {
            log.info("Dry run - not triggering anything.");
            return;
        }

        Set<String> attempted = levels.stream().flatMap(List::stream).collect(Collectors.toCollection(LinkedHashSet::new));

        OrchestrationCoordinator coordinator = new OrchestrationCoordinator(
                new WorkflowTrigger(token), Duration.ofSeconds(args.pollIntervalSeconds()), Duration.ofMinutes(5),
                args.maxRetries(), workflowCandidates);
        Map<String, BuildOutcome> outcomes = coordinator.run(gitHub, levels, prerequisitesOf, dispatchNodes, log::info);

        printSummary(attempted, outcomes, branchFailures);
    }

    private void printPlan(Map<String, ProjectNode> nodes, List<List<String>> levels) {
        log.info("Build order ({} level(s)):", levels.size());
        for (int i = 0; i < levels.size(); i++) {
            String level = levels.get(i).stream()
                    .map(name -> name + "@" + nodes.get(name).branch())
                    .collect(Collectors.joining(", "));
            log.info("  Level {}: {}", i, level);
        }
    }

    private void printSummary(Set<String> attempted, Map<String, BuildOutcome> outcomes,
            Map<String, String> branchFailures) {
        log.info("=== Summary ===");
        for (String project : attempted) {
            log.info("  {} -> {}", project, outcomes.getOrDefault(project, BuildOutcome.ERROR));
        }
        for (String project : branchFailures.keySet()) {
            log.info("  {} -> {}", project, BuildOutcome.SKIPPED_BRANCH_UNRESOLVED);
        }
    }
}
