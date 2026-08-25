package io.github.ryanjbaxter.cireleaseorchestrator.ci;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.ryanjbaxter.cireleaseorchestrator.graph.ProjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrationCoordinatorTest {

    @Mock
    private WorkflowTrigger workflowTrigger;

    @Mock
    private GitHub gitHub;

    @Mock
    private GHRepository repo;

    @Mock
    private GHWorkflowRun run;

    private static final ProjectNode NODE = new ProjectNode("spring-cloud-config", "spring-cloud/spring-cloud-config",
            "main", "5.0.5-SNAPSHOT");

    @Test
    void retriesUntilSuccessWithinTheRetryBudget() throws IOException, InterruptedException {
        when(gitHub.getRepository(NODE.repo())).thenReturn(repo);
        when(workflowTrigger.dispatch(repo, NODE.branch(), WorkflowTrigger.CI_WORKFLOW_CANDIDATES))
                .thenReturn(new WorkflowTrigger.Dispatched("ci.yml", 1L, java.time.Instant.now()));
        when(workflowTrigger.findDispatchedRun(eq(repo), eq(NODE.branch()), any(), any(), any())).thenReturn(run);
        when(workflowTrigger.awaitCompletion(eq(repo), eq(run), any())).thenReturn(GHWorkflowRun.Conclusion.FAILURE);
        // Fails once more, then succeeds on the second retry.
        when(workflowTrigger.rerunAndAwaitCompletion(eq(repo), eq(run), any()))
                .thenReturn(GHWorkflowRun.Conclusion.FAILURE, GHWorkflowRun.Conclusion.SUCCESS);

        OrchestrationCoordinator coordinator = new OrchestrationCoordinator(
                workflowTrigger, Duration.ofMillis(1), Duration.ofSeconds(5), 2, WorkflowTrigger.CI_WORKFLOW_CANDIDATES);

        Map<String, BuildOutcome> outcomes = coordinator.run(gitHub, List.of(List.of(NODE.name())),
                Map.of(NODE.name(), Set.of()), Map.of(NODE.name(), NODE), event -> { });

        assertThat(outcomes.get(NODE.name())).isEqualTo(BuildOutcome.SUCCESS);
        verify(workflowTrigger, times(2)).rerunAndAwaitCompletion(eq(repo), eq(run), any());
    }

    @Test
    void reportsFailureOnceTheRetryBudgetIsExhausted() throws IOException, InterruptedException {
        when(gitHub.getRepository(NODE.repo())).thenReturn(repo);
        when(workflowTrigger.dispatch(repo, NODE.branch(), WorkflowTrigger.CI_WORKFLOW_CANDIDATES))
                .thenReturn(new WorkflowTrigger.Dispatched("ci.yml", 1L, java.time.Instant.now()));
        when(workflowTrigger.findDispatchedRun(eq(repo), eq(NODE.branch()), any(), any(), any())).thenReturn(run);
        when(workflowTrigger.awaitCompletion(eq(repo), eq(run), any())).thenReturn(GHWorkflowRun.Conclusion.FAILURE);
        when(workflowTrigger.rerunAndAwaitCompletion(eq(repo), eq(run), any())).thenReturn(GHWorkflowRun.Conclusion.FAILURE);

        OrchestrationCoordinator coordinator = new OrchestrationCoordinator(
                workflowTrigger, Duration.ofMillis(1), Duration.ofSeconds(5), 1, WorkflowTrigger.CI_WORKFLOW_CANDIDATES);

        Map<String, BuildOutcome> outcomes = coordinator.run(gitHub, List.of(List.of(NODE.name())),
                Map.of(NODE.name(), Set.of()), Map.of(NODE.name(), NODE), event -> { });

        assertThat(outcomes.get(NODE.name())).isEqualTo(BuildOutcome.FAILURE);
        verify(workflowTrigger, times(1)).rerunAndAwaitCompletion(eq(repo), eq(run), any());
    }

    @Test
    void zeroMaxRetriesNeverCallsRerun() throws IOException, InterruptedException {
        when(gitHub.getRepository(NODE.repo())).thenReturn(repo);
        when(workflowTrigger.dispatch(repo, NODE.branch(), WorkflowTrigger.CI_WORKFLOW_CANDIDATES))
                .thenReturn(new WorkflowTrigger.Dispatched("ci.yml", 1L, java.time.Instant.now()));
        when(workflowTrigger.findDispatchedRun(eq(repo), eq(NODE.branch()), any(), any(), any())).thenReturn(run);
        when(workflowTrigger.awaitCompletion(eq(repo), eq(run), any())).thenReturn(GHWorkflowRun.Conclusion.FAILURE);

        OrchestrationCoordinator coordinator = new OrchestrationCoordinator(
                workflowTrigger, Duration.ofMillis(1), Duration.ofSeconds(5), 0, WorkflowTrigger.CI_WORKFLOW_CANDIDATES);

        Map<String, BuildOutcome> outcomes = coordinator.run(gitHub, List.of(List.of(NODE.name())),
                Map.of(NODE.name(), Set.of()), Map.of(NODE.name(), NODE), event -> { });

        assertThat(outcomes.get(NODE.name())).isEqualTo(BuildOutcome.FAILURE);
        verify(workflowTrigger, times(0)).rerunAndAwaitCompletion(any(), any(), any());
    }
}
