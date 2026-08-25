package io.github.ryanjbaxter.cireleaseorchestrator.branch;

/**
 * The result of resolving a project's target branch: either a branch to build on, or a typed
 * failure reason the caller can report and skip past.
 */
public sealed interface BranchResolution {

    record Resolved(String branch) implements BranchResolution {
    }

    record Failed(String reason, String message) implements BranchResolution {
    }
}
