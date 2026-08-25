package io.github.ryanjbaxter.cireleaseorchestrator;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GitHub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Every other test in this suite mocks github-api, so none of them ever load GitHubClient for
// real. That left a blind spot: github-api 1.327 called the deprecated
// PropertyNamingStrategy.SNAKE_CASE from GitHubClient's *static initializer*, and Jackson removed
// that field in 2.20.0. The combination compiled, and the whole suite passed green, but the CLI
// died with NoSuchFieldError on its first API call. Only a runtime class load catches that class
// of breakage - a version constraint in the pom does not, and neither does the compiler.
class GitHubApiCompatibilityTest {

    // GitHub.offline() runs the real client bootstrap - static initializer, ObjectMapper setup and
    // all - without touching the network.
    @Test
    void githubClientBootstrapsAgainstTheResolvedJacksonVersion() {
        assertThatCode(GitHub::offline).doesNotThrowAnyException();
    }

    @Test
    void offlineClientIsUsable() {
        GitHub github = GitHub.offline();

        assertThat(github.isOffline()).isTrue();
    }
}
