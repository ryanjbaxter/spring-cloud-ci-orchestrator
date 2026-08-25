package io.github.ryanjbaxter.cireleaseorchestrator.github;

import java.io.IOException;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class GitHubClientFactory {

    public GitHub create(String token) throws IOException {
        return new GitHubBuilder().withOAuthToken(token).build();
    }
}
