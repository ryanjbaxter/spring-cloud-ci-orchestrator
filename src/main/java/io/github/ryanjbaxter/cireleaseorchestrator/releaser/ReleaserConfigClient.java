package io.github.ryanjbaxter.cireleaseorchestrator.releaser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;

/**
 * Reads the project list and versions for a release train from the {@code jenkins-releaser-config}
 * branch of {@code spring-cloud/spring-cloud-release-commercial}. That repository holds the
 * releaser config for both OSS and commercial trains, so it is used regardless of which repo type
 * the orchestrator is targeting - mirrors {@code update-versions.yml} in spring-cloud-github-actions.
 */
public class ReleaserConfigClient {

    public static final String RELEASE_CONFIG_REPO = "spring-cloud/spring-cloud-release-commercial";
    public static final String RELEASE_CONFIG_BRANCH = "jenkins-releaser-config";

    // Present in the properties file so every project can pick up the Spring Boot version it
    // should build against, but it isn't a Spring Cloud repository and nothing is triggered for it.
    private static final Set<String> NON_REPO_KEYS = Set.of("spring-boot");

    private static final Pattern ENTRY_PATTERN =
            Pattern.compile("^releaser\\.fixed-versions\\[([^\\]]+)\\]=(.+?)\\r?$");

    // 3 or 4 numeric segments, optionally with a qualifier: 2025.1.3, 2025.1.3-SNAPSHOT,
    // 2025.1.0-RC1, 2025.1.2.1, 2025.1.3-INTERNAL-SNAPSHOT - same shape update-versions.yml
    // validates before deriving a file name that could not possibly exist, extended to allow a
    // hyphen inside the qualifier itself (INTERNAL-SNAPSHOT is two words), which real file names
    // on jenkins-releaser-config actually use, e.g. 2025_1_3-internal-snapshot.properties.
    private static final Pattern TRAIN_VERSION_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+){2,3})(-[A-Za-z][\\w.-]*)?$");

    /**
     * Converts a release train version to the properties file name used on the
     * jenkins-releaser-config branch, e.g. "2025.1.3" -&gt; "2025_1_3.properties",
     * "2025.1.3-SNAPSHOT" -&gt; "2025_1_3-snapshot.properties".
     */
    public String fileNameFor(String releaseTrainVersion) {
        Matcher matcher = TRAIN_VERSION_PATTERN.matcher(releaseTrainVersion);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "release-train-version must be 3 or 4 numeric segments with an optional qualifier "
                            + "(e.g. 2025.1.3-SNAPSHOT or 2025.1.2); got '" + releaseTrainVersion + "'");
        }
        String numeric = matcher.group(1);
        String qualifier = matcher.group(2);
        String suffix = qualifier == null ? "" : qualifier.toLowerCase(Locale.ROOT);
        return (numeric + suffix).replace('.', '_') + ".properties";
    }

    /**
     * Parses {@code releaser.fixed-versions[project]=version} lines out of the raw properties file
     * content. Lines that don't match (blank lines, comments, unrelated properties) are ignored.
     */
    public Map<String, String> parse(String content) {
        Map<String, String> versions = new LinkedHashMap<>();
        for (String line : content.split("\n", -1)) {
            Matcher matcher = ENTRY_PATTERN.matcher(line);
            if (matcher.matches()) {
                versions.put(matcher.group(1).strip(), matcher.group(2).strip());
            }
        }
        return versions;
    }

    /**
     * Fetches and parses the properties file for a release train, with non-repository keys
     * (currently just {@code spring-boot}) filtered out.
     */
    public Map<String, String> fetchProjectVersions(GitHub gitHub, String releaseTrainVersion) throws IOException {
        String fileName = fileNameFor(releaseTrainVersion);
        GHContent content = gitHub.getRepository(RELEASE_CONFIG_REPO).getFileContent(fileName, RELEASE_CONFIG_BRANCH);
        Map<String, String> versions = parse(content.getContent());
        if (versions.isEmpty()) {
            throw new IllegalStateException(fileName + " contains no releaser.fixed-versions[...] entries.");
        }
        Map<String, String> filtered = new LinkedHashMap<>(versions);
        NON_REPO_KEYS.forEach(filtered::remove);
        return filtered;
    }
}
