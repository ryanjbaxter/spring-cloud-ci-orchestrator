package io.github.ryanjbaxter.cireleaseorchestrator.orchestration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.ryanjbaxter.cireleaseorchestrator.branch.BranchResolution;
import io.github.ryanjbaxter.cireleaseorchestrator.branch.BranchResolver;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomFetcher;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphResolverTest {

    @Mock
    private BranchResolver branchResolver;

    @Mock
    private PomFetcher pomFetcher;

    @Mock
    private GitHub gitHub;

    @Mock
    private GHRepository buildRepo;

    @Mock
    private GHRepository configRepo;

    // Must be built in @BeforeEach, not a field initializer: field initializers run before
    // MockitoExtension injects the @Mock fields above, so branchResolver/pomFetcher would still be
    // null at construction time.
    private GraphResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GraphResolver(branchResolver, pomFetcher);
    }

    @Test
    void resolvesEachProjectIndependentlyAndBuildsTheGraphFromResolvedBranches() throws IOException {
        Map<String, String> versions = Map.of("spring-cloud-build", "5.0.3", "spring-cloud-config", "5.0.5");

        when(gitHub.getRepository("spring-cloud/spring-cloud-build")).thenReturn(buildRepo);
        when(gitHub.getRepository("spring-cloud/spring-cloud-config")).thenReturn(configRepo);

        when(branchResolver.resolve(eq(buildRepo), eq("5.0.3"), anyBoolean()))
                .thenReturn(new BranchResolution.Resolved("main"));
        when(branchResolver.resolve(eq(configRepo), eq("5.0.5"), anyBoolean()))
                .thenReturn(new BranchResolution.Failed("branch-not-found", "no branch for spring-cloud-config"));

        when(pomFetcher.fetchAllPoms(buildRepo, "main"))
                .thenReturn(List.of(new PomInfo("spring-cloud-build-parent", null, "5.0.3-SNAPSHOT", null, Map.of())));

        GraphResolver.Resolved resolved = resolver.resolve(gitHub, versions, versions.keySet(), false);

        assertThat(resolved.nodes()).containsOnlyKeys("spring-cloud-build");
        assertThat(resolved.nodes().get("spring-cloud-build").branch()).isEqualTo("main");
        assertThat(resolved.branchFailures()).containsEntry("spring-cloud-config", "no branch for spring-cloud-config");
        assertThat(resolved.levels()).containsExactly(List.of("spring-cloud-build"));
    }

    @Test
    void missingRepositoryIsRecordedAsABranchFailureWithoutAttemptingBranchResolution() throws IOException {
        Map<String, String> versions = Map.of("spring-cloud-ghost", "1.0.0");
        when(gitHub.getRepository("spring-cloud/spring-cloud-ghost")).thenThrow(new GHFileNotFoundException());

        GraphResolver.Resolved resolved = resolver.resolve(gitHub, versions, versions.keySet(), false);

        assertThat(resolved.nodes()).isEmpty();
        assertThat(resolved.branchFailures().get("spring-cloud-ghost")).contains("repository not found");
    }
}
