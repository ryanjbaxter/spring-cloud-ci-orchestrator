package io.github.ryanjbaxter.cireleaseorchestrator.cli;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.stereotype.Component;

import io.github.ryanjbaxter.cireleaseorchestrator.branch.BranchResolver;
import io.github.ryanjbaxter.cireleaseorchestrator.ci.WorkflowTrigger;
import io.github.ryanjbaxter.cireleaseorchestrator.github.GitHubClientFactory;
import io.github.ryanjbaxter.cireleaseorchestrator.github.GitHubTokenResolver;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomFetcher;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.ProjectNode;
import io.github.ryanjbaxter.cireleaseorchestrator.orchestration.GraphResolver;
import io.github.ryanjbaxter.cireleaseorchestrator.orchestration.OrchestrationCommand;
import io.github.ryanjbaxter.cireleaseorchestrator.releaser.ReleaserConfigClient;

@Component
public class OrchestratorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorRunner.class);

    // Fixed by convention across every Spring Cloud project - see README-deploy-docs.md and
    // confirmed live: both spring-cloud-commons and spring-cloud-config-commercial carry
    // .github/workflows/deploy-docs.yml on this exact branch name.
    private static final String DOCS_BUILD_BRANCH = "docs-build";

    @Override
    public void run(String... rawArgs) {
        try {
            execute(OrchestratorArgs.parse(new DefaultApplicationArguments(rawArgs)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            log.error("GitHub API error: {}", e.getMessage());
            System.exit(1);
        }
    }

    private void execute(OrchestratorArgs orchestratorArgs) throws IOException {
        String token = new GitHubTokenResolver().resolve(orchestratorArgs.token());
        GitHub gitHub = new GitHubClientFactory().create(token);
        boolean commercial = orchestratorArgs.repoType() == OrchestratorArgs.RepoType.COMMERCIAL;

        ReleaserConfigClient releaserConfigClient = new ReleaserConfigClient();
        log.info("Fetching releaser config for release train {}...", orchestratorArgs.releaseTrainVersion());
        Map<String, String> versions = releaserConfigClient.fetchProjectVersions(gitHub, orchestratorArgs.releaseTrainVersion());

        Set<String> requested = orchestratorArgs.projectsFilter() != null
                ? orchestratorArgs.projectsFilter()
                : versions.keySet();
        Set<String> unknown = requested.stream()
                .filter(project -> !versions.containsKey(project))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown project(s): " + unknown + ". Known projects: " + new TreeSet<>(versions.keySet()));
        }

        GraphResolver graphResolver = new GraphResolver(new BranchResolver(new PomFetcher()), new PomFetcher());
        OrchestrationCommand command = new OrchestrationCommand(graphResolver);

        switch (orchestratorArgs.command()) {
            case TRIGGER_CI -> command.run(gitHub, token, orchestratorArgs, versions, requested, commercial,
                    ProjectNode::branch, WorkflowTrigger.CI_WORKFLOW_CANDIDATES);
            case DEPLOY_DOCS -> command.run(gitHub, token, orchestratorArgs, versions, requested, commercial,
                    node -> DOCS_BUILD_BRANCH, WorkflowTrigger.DOCS_WORKFLOW_CANDIDATES);
        }
    }
}
