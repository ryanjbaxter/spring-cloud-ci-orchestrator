package io.github.ryanjbaxter.cireleaseorchestrator.orchestration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GitHub;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.ryanjbaxter.cireleaseorchestrator.ci.WorkflowTrigger;
import io.github.ryanjbaxter.cireleaseorchestrator.cli.OrchestratorArgs;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.ProjectNode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrationCommandTest {

    @Mock
    private GraphResolver graphResolver;

    @Mock
    private GitHub gitHub;

    // Must be built in @BeforeEach, not a field initializer: field initializers run before
    // MockitoExtension injects the @Mock fields above, so graphResolver would still be null.
    private OrchestrationCommand command;

    @BeforeEach
    void setUp() {
        command = new OrchestrationCommand(graphResolver);
    }

    private static final ProjectNode BUILD = new ProjectNode("spring-cloud-build", "spring-cloud/spring-cloud-build",
            "main", "5.0.3-SNAPSHOT");
    private static final ProjectNode COMMONS = new ProjectNode("spring-cloud-commons", "spring-cloud/spring-cloud-commons",
            "main", "5.0.3-SNAPSHOT");

    @Test
    void rejectsAResumeFromTargetThatWasNotResolved() throws IOException {
        Map<String, ProjectNode> nodes = new LinkedHashMap<>();
        nodes.put(BUILD.name(), BUILD);
        when(graphResolver.resolve(gitHub, Map.of(), Set.of(BUILD.name()), false))
                .thenReturn(new GraphResolver.Resolved(nodes, Map.of(),
                        Map.of(BUILD.name(), Set.of()), Map.of(BUILD.name(), Set.of()), List.of(List.of(BUILD.name()))));

        OrchestratorArgs args = new OrchestratorArgs(OrchestratorArgs.Command.TRIGGER_CI, "2025.1.3",
                OrchestratorArgs.RepoType.OSS, null, "does-not-exist", true, null, 45, 1);

        assertThatThrownBy(() -> command.run(gitHub, "token", args, Map.of(), Set.of(BUILD.name()), false,
                ProjectNode::branch, WorkflowTrigger.CI_WORKFLOW_CANDIDATES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void dryRunWithAValidPlanDoesNotThrowRegardlessOfDispatchBranchMapping() throws IOException {
        Map<String, ProjectNode> nodes = new LinkedHashMap<>();
        nodes.put(BUILD.name(), BUILD);
        nodes.put(COMMONS.name(), COMMONS);
        when(graphResolver.resolve(gitHub, Map.of(), Set.of(BUILD.name(), COMMONS.name()), false))
                .thenReturn(new GraphResolver.Resolved(nodes, Map.of(),
                        Map.of(BUILD.name(), Set.of(COMMONS.name()), COMMONS.name(), Set.of()),
                        Map.of(BUILD.name(), Set.of(), COMMONS.name(), Set.of(BUILD.name())),
                        List.of(List.of(BUILD.name()), List.of(COMMONS.name()))));

        OrchestratorArgs args = new OrchestratorArgs(OrchestratorArgs.Command.DEPLOY_DOCS, "2025.1.3",
                OrchestratorArgs.RepoType.OSS, null, null, true, null, 45, 1);

        // Uses a constant "docs-build" dispatch branch, unrelated to each node's own resolved
        // branch - this is exactly the docs-deploy mapping, exercised here without a real token
        // or network access since dry-run returns before the coordinator is ever built.
        assertThatCode(() -> command.run(gitHub, "token", args, Map.of(), Set.of(BUILD.name(), COMMONS.name()), false,
                node -> "docs-build", WorkflowTrigger.DOCS_WORKFLOW_CANDIDATES))
                .doesNotThrowAnyException();
    }
}
