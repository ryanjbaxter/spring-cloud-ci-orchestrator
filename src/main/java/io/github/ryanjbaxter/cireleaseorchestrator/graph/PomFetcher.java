package io.github.ryanjbaxter.cireleaseorchestrator.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;

/**
 * Fetches pom.xml files from a repository branch via the GitHub API.
 */
public class PomFetcher {

    /**
     * Fetches just the root pom.xml - used by branch resolution, which only ever needs to know
     * a repo's own project version at a given ref.
     */
    public PomInfo fetchRootPom(GHRepository repo, String ref) throws IOException {
        GHContent content = repo.getFileContent("pom.xml", ref);
        return PomXml.parse(content.getContent());
    }

    /**
     * Fetches every pom.xml in the repository (root and modules) at a branch, for dependency-graph
     * derivation. Any module's pom can reference a sibling project's version, not just the root.
     */
    public List<PomInfo> fetchAllPoms(GHRepository repo, String branchName) throws IOException {
        GHBranch branch = repo.getBranch(branchName);
        GHTree tree = repo.getTreeRecursive(branch.getSHA1(), 1);

        List<PomInfo> poms = new ArrayList<>();
        for (GHTreeEntry entry : tree.getTree()) {
            if (!"blob".equals(entry.getType())) {
                continue;
            }
            String path = entry.getPath();
            if (!path.equals("pom.xml") && !path.endsWith("/pom.xml")) {
                continue;
            }
            if (isExcluded(path)) {
                continue;
            }
            GHContent content = repo.getFileContent(path, branchName);
            poms.add(PomXml.parse(content.getContent()));
        }
        return poms;
    }

    /**
     * Fixture and sample poms aren't real project modules and can carry unrelated version
     * properties that would pollute the graph - e.g. spring-cloud-function's sample modules
     * reference spring-cloud-stream, which closes a cycle with stream's binders referencing function.
     */
    static boolean isExcluded(String pomPath) {
        return pomPath.contains("/src/test/") || pomPath.contains("/src/it/")
                || pomPath.startsWith("samples/") || pomPath.contains("/samples/")
                || pomPath.contains("-samples/");
    }
}
