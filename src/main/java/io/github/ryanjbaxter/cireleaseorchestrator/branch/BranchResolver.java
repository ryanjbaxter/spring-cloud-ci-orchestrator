package io.github.ryanjbaxter.cireleaseorchestrator.branch;

import java.io.IOException;
import java.util.Arrays;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;

import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomFetcher;
import io.github.ryanjbaxter.cireleaseorchestrator.graph.PomInfo;

/**
 * Ports the branch-resolution algorithm already in production use in
 * {@code update-versions.yml}'s "Resolve target branch" step (spring-cloud-github-actions):
 * derive {@code <major>.<minor>.x} from the project's version, use it if it exists, and for OSS
 * repos only, fall back to {@code main} when main's own pom version is genuinely on that line.
 * Commercial repos get no fallback - they have no shared main branch to fall back to.
 */
public class BranchResolver {

    private final PomFetcher pomFetcher;

    public BranchResolver(PomFetcher pomFetcher) {
        this.pomFetcher = pomFetcher;
    }

    /**
     * Drops the last version segment and appends {@code .x}, after stripping a {@code -SNAPSHOT}
     * suffix. e.g. {@code 5.0.4-SNAPSHOT} -&gt; {@code 5.0.x}, {@code 4.2.9} -&gt; {@code 4.2.x}.
     */
    public String targetBranchFor(String version) {
        String[] parts = plainVersion(version).split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Cannot derive a branch from version '" + version + "'");
        }
        return String.join(".", Arrays.copyOf(parts, parts.length - 1)) + ".x";
    }

    public BranchResolution resolve(GHRepository repo, String version, boolean commercial) throws IOException {
        String target = targetBranchFor(version);
        if (branchExists(repo, target)) {
            return new BranchResolution.Resolved(target);
        }

        // Commercial repos on an actively-developed line often carry their work on
        // <major>.<minor>.x-internal rather than the plain <major>.<minor>.x update-versions.yml
        // targets - the branch name still pins the exact line, so no extra version check is
        // needed here (unlike the main fallback below, which isn't tied to any one line by name).
        String internalTarget = target + "-internal";
        if (branchExists(repo, internalTarget)) {
            return new BranchResolution.Resolved(internalTarget);
        }

        if (commercial) {
            return new BranchResolution.Failed("branch-not-found",
                    "No " + target + " or " + internalTarget + " branch on " + repo.getFullName()
                            + ", and commercial repos have no main to fall back to.");
        }

        if (!branchExists(repo, "main")) {
            return new BranchResolution.Failed("branch-not-found",
                    "No " + target + " or main branch on " + repo.getFullName() + ".");
        }

        String expected = expectedMajorMinor(version);
        PomInfo mainPom;
        try {
            mainPom = pomFetcher.fetchRootPom(repo, "main");
        } catch (IOException e) {
            return new BranchResolution.Failed("branch-not-found",
                    "No " + target + " branch and could not read pom.xml on main: " + e.getMessage());
        }

        String pomVersion = mainPom.effectiveVersion();
        if (pomVersion == null || !pomVersion.startsWith(expected + ".")) {
            return new BranchResolution.Failed("version-mismatch",
                    "No " + target + " branch, and main is at '" + pomVersion + "', which is not on the "
                            + expected + " line. Refusing to guess.");
        }

        return new BranchResolution.Resolved("main");
    }

    private String plainVersion(String version) {
        return version.endsWith("-SNAPSHOT") ? version.substring(0, version.length() - "-SNAPSHOT".length()) : version;
    }

    private String expectedMajorMinor(String version) {
        String[] parts = plainVersion(version).split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Cannot derive major.minor from version '" + version + "'");
        }
        return parts[0] + "." + parts[1];
    }

    private boolean branchExists(GHRepository repo, String branch) throws IOException {
        try {
            repo.getBranch(branch);
            return true;
        } catch (GHFileNotFoundException e) {
            return false;
        }
    }
}
