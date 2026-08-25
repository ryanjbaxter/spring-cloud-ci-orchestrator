package io.github.ryanjbaxter.cireleaseorchestrator.ci;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;

import io.github.ryanjbaxter.cireleaseorchestrator.graph.ProjectNode;

/**
 * Walks the topological levels produced by {@link io.github.ryanjbaxter.cireleaseorchestrator.graph.TopologicalSorter},
 * triggering and waiting for each level's projects concurrently (a virtual thread per project) and
 * blocking a project's build whenever any of its prerequisites didn't succeed - the
 * block-descendants-by-default policy the design doc recommends. This is a level-by-level barrier
 * rather than a fully dynamic per-edge scheduler: simpler, and still gives real concurrency for the
 * common case of several independent projects sharing one prerequisite.
 */
public class OrchestrationCoordinator {

    private final WorkflowTrigger workflowTrigger;
    private final Duration pollInterval;
    private final Duration dispatchTimeout;
    private final int maxRetries;
    private final List<String> workflowCandidates;

    public OrchestrationCoordinator(WorkflowTrigger workflowTrigger, Duration pollInterval, Duration dispatchTimeout,
            int maxRetries, List<String> workflowCandidates) {
        this.workflowTrigger = workflowTrigger;
        this.pollInterval = pollInterval;
        this.dispatchTimeout = dispatchTimeout;
        this.maxRetries = maxRetries;
        this.workflowCandidates = workflowCandidates;
    }

    public Map<String, BuildOutcome> run(GitHub gitHub, List<List<String>> levels,
            Map<String, Set<String>> prerequisitesOf, Map<String, ProjectNode> nodes, Consumer<String> onEvent) {
        Map<String, BuildOutcome> outcomes = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<String> level : levels) {
                List<Future<?>> futures = new ArrayList<>();
                for (String name : level) {
                    futures.add(executor.submit(() ->
                            outcomes.put(name, runOne(gitHub, name, prerequisitesOf, nodes, outcomes, onEvent))));
                }
                awaitAll(futures);
            }
        }

        return outcomes;
    }

    private void awaitAll(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                // The failure is already captured as this project's outcome by runOne; nothing
                // further to do here except let the remaining futures in the level finish.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private BuildOutcome runOne(GitHub gitHub, String name, Map<String, Set<String>> prerequisitesOf,
            Map<String, ProjectNode> nodes, Map<String, BuildOutcome> outcomes, Consumer<String> onEvent) {
        boolean blocked = prerequisitesOf.getOrDefault(name, Set.of()).stream()
                .anyMatch(prerequisite -> outcomes.get(prerequisite) != BuildOutcome.SUCCESS);
        if (blocked) {
            onEvent.accept(name + ": skipped (a prerequisite did not succeed)");
            return BuildOutcome.SKIPPED_DEPENDENCY_FAILED;
        }

        ProjectNode node = nodes.get(name);
        try {
            GHRepository repo = gitHub.getRepository(node.repo());
            onEvent.accept(name + ": dispatching workflow on " + node.repo() + "@" + node.branch());
            WorkflowTrigger.Dispatched dispatched = workflowTrigger.dispatch(repo, node.branch(), workflowCandidates);

            GHWorkflowRun run = workflowTrigger.findDispatchedRun(repo, node.branch(), dispatched, dispatchTimeout, pollInterval);
            onEvent.accept(name + ": run started (" + dispatched.workflowFile() + "), waiting for completion...");

            GHWorkflowRun.Conclusion conclusion = workflowTrigger.awaitCompletion(repo, run, pollInterval);

            int retries = 0;
            while (conclusion != GHWorkflowRun.Conclusion.SUCCESS && retries < maxRetries) {
                retries++;
                onEvent.accept(name + ": finished with conclusion " + conclusion + ", retrying (" + retries + "/" + maxRetries + ")...");
                conclusion = workflowTrigger.rerunAndAwaitCompletion(repo, run, pollInterval);
            }

            onEvent.accept(name + ": finished with conclusion " + conclusion
                    + (retries > 0 ? " after " + retries + " retry/retries" : ""));
            return conclusion == GHWorkflowRun.Conclusion.SUCCESS ? BuildOutcome.SUCCESS : BuildOutcome.FAILURE;
        } catch (IOException e) {
            onEvent.accept(name + ": error - " + e.getMessage());
            return BuildOutcome.ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onEvent.accept(name + ": interrupted");
            return BuildOutcome.ERROR;
        }
    }
}
