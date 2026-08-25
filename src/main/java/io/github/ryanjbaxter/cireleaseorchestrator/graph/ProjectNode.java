package io.github.ryanjbaxter.cireleaseorchestrator.graph;

/**
 * A project that is in scope for this run, with its GitHub repo, resolved branch, and the version
 * it should build at.
 */
public record ProjectNode(String name, String repo, String branch, String version) {
}
