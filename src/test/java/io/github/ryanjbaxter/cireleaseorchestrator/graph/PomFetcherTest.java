package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PomFetcherTest {

    @Test
    void excludesTestFixtureAndSamplePoms() {
        assertThat(PomFetcher.isExcluded("module/src/test/resources/pom.xml")).isTrue();
        assertThat(PomFetcher.isExcluded("module/src/it/basic/pom.xml")).isTrue();
        assertThat(PomFetcher.isExcluded("spring-cloud-function-samples/function-sample/pom.xml")).isTrue();
        assertThat(PomFetcher.isExcluded("samples/sample-app/pom.xml")).isTrue();
        assertThat(PomFetcher.isExcluded("docs/samples/app/pom.xml")).isTrue();
    }

    @Test
    void keepsRealModulePoms() {
        assertThat(PomFetcher.isExcluded("pom.xml")).isFalse();
        assertThat(PomFetcher.isExcluded("binders/rabbit-binder/pom.xml")).isFalse();
        assertThat(PomFetcher.isExcluded("spring-cloud-dependencies/pom.xml")).isFalse();
    }
}
