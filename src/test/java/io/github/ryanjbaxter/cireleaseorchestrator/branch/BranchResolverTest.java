package io.github.ryanjbaxter.cireleaseorchestrator.branch;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomFetcher;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchResolverTest {

    @Mock
    private PomFetcher pomFetcher;

    @Mock
    private GHRepository repo;

    private BranchResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BranchResolver(pomFetcher);
        lenient().when(repo.getFullName()).thenReturn("spring-cloud/spring-cloud-config");
    }

    @Test
    void targetBranchDropsLastSegmentAndAppendsX() {
        assertThat(resolver.targetBranchFor("5.0.4")).isEqualTo("5.0.x");
        assertThat(resolver.targetBranchFor("4.2.9-SNAPSHOT")).isEqualTo("4.2.x");
        assertThat(resolver.targetBranchFor("2025.1.3")).isEqualTo("2025.1.x");
    }

    @Test
    void usesTargetBranchWhenItExists() throws IOException {
        when(repo.getBranch("5.0.x")).thenReturn(null);

        BranchResolution resolution = resolver.resolve(repo, "5.0.4-SNAPSHOT", false);

        assertThat(resolution).isEqualTo(new BranchResolution.Resolved("5.0.x"));
    }

    @Test
    void fallsBackToInternalBranchWhenPlainTargetIsMissing() throws IOException {
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x");
        when(repo.getBranch("5.0.x-internal")).thenReturn(null);

        BranchResolution resolution = resolver.resolve(repo, "5.0.4-SNAPSHOT", true);

        assertThat(resolution).isEqualTo(new BranchResolution.Resolved("5.0.x-internal"));
    }

    @Test
    void commercialRepoGetsNoMainFallback() throws IOException {
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("4.2.x");
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("4.2.x-internal");

        BranchResolution resolution = resolver.resolve(repo, "4.2.9-SNAPSHOT", true);

        assertThat(resolution).isInstanceOf(BranchResolution.Failed.class);
        assertThat(((BranchResolution.Failed) resolution).reason()).isEqualTo("branch-not-found");
    }

    @Test
    void ossRepoFallsBackToMainWhenVersionMatches() throws IOException {
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x");
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x-internal");
        when(repo.getBranch("main")).thenReturn(null);
        when(pomFetcher.fetchRootPom(eq(repo), eq("main")))
                .thenReturn(new PomInfo("spring-cloud-function", null, "5.0.4-SNAPSHOT", null, Map.of()));

        BranchResolution resolution = resolver.resolve(repo, "5.0.4-SNAPSHOT", false);

        assertThat(resolution).isEqualTo(new BranchResolution.Resolved("main"));
    }

    @Test
    void ossRepoRejectsMainOnADifferentLine() throws IOException {
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x");
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x-internal");
        when(repo.getBranch("main")).thenReturn(null);
        when(pomFetcher.fetchRootPom(eq(repo), eq("main")))
                .thenReturn(new PomInfo("spring-cloud-function", null, "6.1.0-SNAPSHOT", null, Map.of()));

        BranchResolution resolution = resolver.resolve(repo, "5.0.4-SNAPSHOT", false);

        assertThat(resolution).isInstanceOf(BranchResolution.Failed.class);
        assertThat(((BranchResolution.Failed) resolution).reason()).isEqualTo("version-mismatch");
    }

    @Test
    void ossRepoFailsWhenNeitherTargetNorInternalNorMainExist() throws IOException {
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x");
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("5.0.x-internal");
        doThrow(new GHFileNotFoundException()).when(repo).getBranch("main");

        BranchResolution resolution = resolver.resolve(repo, "5.0.4-SNAPSHOT", false);

        assertThat(resolution).isInstanceOf(BranchResolution.Failed.class);
        assertThat(((BranchResolution.Failed) resolution).reason()).isEqualTo("branch-not-found");
    }
}
