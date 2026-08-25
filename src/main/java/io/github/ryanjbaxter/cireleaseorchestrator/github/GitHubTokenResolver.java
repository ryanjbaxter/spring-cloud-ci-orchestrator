package io.github.ryanjbaxter.cireleaseorchestrator.github;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Resolves the GitHub token to use: an explicit {@code --token} argument, then the
 * {@code ORCHESTRATOR_GITHUB_TOKEN} environment variable, then whatever {@code gh auth token}
 * reports for the developer running this locally. Every developer supplies their own token this
 * way - there is no shared secret and nothing is stored by this app.
 */
public class GitHubTokenResolver {

    public static final String ENV_VAR = "ORCHESTRATOR_GITHUB_TOKEN";

    public String resolve(String explicitToken) {
        if (explicitToken != null && !explicitToken.isBlank()) {
            return explicitToken.strip();
        }

        String envToken = System.getenv(ENV_VAR);
        if (envToken != null && !envToken.isBlank()) {
            return envToken.strip();
        }

        return fromGhCli();
    }

    private String fromGhCli() {
        try {
            Process process = new ProcessBuilder("gh", "auth", "token").start();
            String output;
            try (BufferedReader reader = process.inputReader()) {
                output = reader.readLine();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 || output == null || output.isBlank()) {
                throw new IllegalStateException(
                        "Could not resolve a GitHub token: pass --token, set " + ENV_VAR
                                + ", or run 'gh auth login'.");
            }
            return output.strip();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not run 'gh auth token' - is the GitHub CLI installed? "
                            + "Pass --token or set " + ENV_VAR + " instead.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving a GitHub token", e);
        }
    }
}
