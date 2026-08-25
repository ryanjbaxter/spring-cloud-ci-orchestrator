package io.github.ryanjbaxter.cireleaseorchestrator.ci;

public enum BuildOutcome {
    SUCCESS,
    FAILURE,
    SKIPPED_DEPENDENCY_FAILED,
    SKIPPED_BRANCH_UNRESOLVED,
    ERROR
}
