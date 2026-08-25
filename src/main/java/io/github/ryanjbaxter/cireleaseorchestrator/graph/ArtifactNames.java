package io.github.ryanjbaxter.cireleaseorchestrator.graph;

/**
 * Converts a Maven artifactId to the project name used elsewhere (properties file keys, repo
 * names) by stripping the common BOM/parent suffixes that aren't part of the project name itself.
 * e.g. spring-cloud-build-dependencies -&gt; spring-cloud-build.
 */
public final class ArtifactNames {

    private ArtifactNames() {
    }

    public static String toProjectName(String artifactId) {
        if (artifactId == null) {
            return null;
        }
        String name = artifactId;
        if (name.endsWith("-dependencies")) {
            name = name.substring(0, name.length() - "-dependencies".length());
        }
        if (name.endsWith("-parent")) {
            name = name.substring(0, name.length() - "-parent".length());
        }
        return name;
    }
}
