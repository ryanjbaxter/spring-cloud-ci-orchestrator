package io.github.ryanjbaxter.cireleaseorchestrator.cli;

/**
 * Checked in {@code main()} before {@code SpringApplication.run(...)} - {@code --help}/{@code -h}
 * prints and exits without ever starting the Spring context, so the output is just the usage text,
 * not the Spring Boot banner and startup logs ahead of it.
 */
public final class Usage {

    private Usage() {
    }

    public static boolean isRequested(String[] args) {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                return true;
            }
        }
        return false;
    }

    public static void print() {
        System.out.println(TEXT);
    }

    private static final String TEXT = """
            ci-release-orchestrator - trigger cross-repo Spring Cloud CI builds and docs deploys, \
            in dependency order, from a developer's laptop.

            Usage:
              java -jar ci-release-orchestrator.jar --release-train-version=<version> --repo-type=<oss|commercial> [options]

            Required:
              --release-train-version=<version>  Release train version to read from the jenkins-releaser-config
                                                  properties file on spring-cloud/spring-cloud-release-commercial,
                                                  e.g. 2025.1.3, 2025.1.3-SNAPSHOT, 2025.1.3-INTERNAL-SNAPSHOT.
              --repo-type=<oss|commercial>        Which repos to target: spring-cloud/<project> (oss) or
                                                  spring-cloud/<project>-commercial (commercial).

            Options:
              --command=<trigger-ci|deploy-docs>  What to trigger. Default: trigger-ci.
                                                     trigger-ci   Dispatch each project's CI workflow
                                                                  (ci-release.yml, ci.yml, or ci.yaml) on its
                                                                  resolved release-train branch.
                                                     deploy-docs  Dispatch deploy-docs.yml on each project's
                                                                  docs-build branch. Ordering still comes from
                                                                  the real dependency graph on the resolved
                                                                  release-train branch - docs-build poms don't
                                                                  carry real cross-project dependency info.
              --projects=<a,b,c>                  Comma-separated project names to restrict to (properties-file
                                                  keys, e.g. spring-cloud-build,spring-cloud-config). Default:
                                                  every project in the properties file.
              --resume-from=<project>             Resume a previous run: only (re)triggers this project and
                                                  everything that transitively depends on it. Everything else
                                                  is treated as already handled and left alone.
              --dry-run                           Resolve branches, fetch poms, and print the dependency-ordered
                                                  plan without triggering or waiting on anything.
              --token=<token>                     GitHub token to use. Falls back to the
                                                  ORCHESTRATOR_GITHUB_TOKEN environment variable, then to
                                                  `gh auth token`.
              --poll-interval-seconds=<n>         How often to poll a triggered run for completion. Default: 45.
              --max-retries=<n>                   Automatic retries (GitHub's rerun-failed-jobs) for a run that
                                                  doesn't finish with a successful conclusion. Default: 1.
                                                  Set to 0 to disable.
              --help, -h                          Print this message and exit.

            Examples:

              Preview the build order for a commercial internal-snapshot train without triggering anything:
                java -jar ci-release-orchestrator.jar \\
                  --release-train-version=2025.1.3-INTERNAL-SNAPSHOT --repo-type=commercial --dry-run

              Trigger CI for two projects only:
                java -jar ci-release-orchestrator.jar \\
                  --release-train-version=2025.1.3 --repo-type=oss \\
                  --projects=spring-cloud-build,spring-cloud-commons

              Resume after spring-cloud-config failed partway through a run:
                java -jar ci-release-orchestrator.jar \\
                  --release-train-version=2025.1.3 --repo-type=commercial --resume-from=spring-cloud-config

              Redeploy docs for every project on a train, in dependency order:
                java -jar ci-release-orchestrator.jar \\
                  --command=deploy-docs --release-train-version=2025.1.3 --repo-type=oss
            """;
}
