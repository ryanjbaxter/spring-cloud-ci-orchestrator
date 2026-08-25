package io.github.ryanjbaxter.cireleaseorchestrator.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrchestratorArgsTest {

    @Test
    void parsesRequiredAndDefaultedOptions() {
        OrchestratorArgs args = OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--release-train-version=2025.1.3", "--repo-type=commercial"));

        assertThat(args.command()).isEqualTo(OrchestratorArgs.Command.TRIGGER_CI);
        assertThat(args.releaseTrainVersion()).isEqualTo("2025.1.3");
        assertThat(args.repoType()).isEqualTo(OrchestratorArgs.RepoType.COMMERCIAL);
        assertThat(args.projectsFilter()).isNull();
        assertThat(args.resumeFrom()).isNull();
        assertThat(args.dryRun()).isFalse();
        assertThat(args.token()).isNull();
        assertThat(args.pollIntervalSeconds()).isEqualTo(45);
        assertThat(args.maxRetries()).isEqualTo(1);
    }

    @Test
    void parsesProjectsFilterAndDryRunAndOverrides() {
        OrchestratorArgs args = OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--release-train-version=2025.1.3",
                "--repo-type=oss",
                "--projects=spring-cloud-build, spring-cloud-config",
                "--dry-run",
                "--token=ghp_abc123",
                "--poll-interval-seconds=30"));

        assertThat(args.projectsFilter()).containsExactly("spring-cloud-build", "spring-cloud-config");
        assertThat(args.dryRun()).isTrue();
        assertThat(args.token()).isEqualTo("ghp_abc123");
        assertThat(args.pollIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void parsesResumeFrom() {
        OrchestratorArgs args = OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--release-train-version=2025.1.3",
                "--repo-type=commercial",
                "--resume-from=spring-cloud-config"));

        assertThat(args.resumeFrom()).isEqualTo("spring-cloud-config");
    }

    @Test
    void parsesMaxRetries() {
        OrchestratorArgs args = OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--release-train-version=2025.1.3", "--repo-type=oss", "--max-retries=3"));

        assertThat(args.maxRetries()).isEqualTo(3);
    }

    @Test
    void rejectsNegativeMaxRetries() {
        assertThatThrownBy(() -> OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--release-train-version=2025.1.3", "--repo-type=oss", "--max-retries=-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--max-retries");
    }

    @Test
    void parsesDeployDocsCommand() {
        OrchestratorArgs args = OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--command=deploy-docs", "--release-train-version=2025.1.3", "--repo-type=oss"));

        assertThat(args.command()).isEqualTo(OrchestratorArgs.Command.DEPLOY_DOCS);
    }

    @Test
    void rejectsUnknownCommand() {
        assertThatThrownBy(() -> OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--command=bogus", "--release-train-version=2025.1.3", "--repo-type=oss")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--command");
    }

    @Test
    void requiresReleaseTrainVersion() {
        assertThatThrownBy(() -> OrchestratorArgs.parse(new DefaultApplicationArguments("--repo-type=oss")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("release-train-version");
    }

    @Test
    void rejectsUnknownRepoType() {
        assertThatThrownBy(() -> OrchestratorArgs.parse(new DefaultApplicationArguments(
                "--release-train-version=2025.1.3", "--repo-type=bogus")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--repo-type");
    }
}
