package io.github.ryanjbaxter.cireleaseorchestrator.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsageTest {

    @Test
    void detectsHelpFlagInEitherForm() {
        assertThat(Usage.isRequested(new String[] {"--help"})).isTrue();
        assertThat(Usage.isRequested(new String[] {"-h"})).isTrue();
        assertThat(Usage.isRequested(new String[] {"--release-train-version=2025.1.3", "--help"})).isTrue();
    }

    @Test
    void doesNotTriggerOnUnrelatedArgs() {
        assertThat(Usage.isRequested(new String[] {})).isFalse();
        assertThat(Usage.isRequested(new String[] {"--release-train-version=2025.1.3", "--repo-type=oss"})).isFalse();
        // Must be an exact flag match, not a substring of some other option's value.
        assertThat(Usage.isRequested(new String[] {"--token=needs-help-desk-approval"})).isFalse();
    }
}
