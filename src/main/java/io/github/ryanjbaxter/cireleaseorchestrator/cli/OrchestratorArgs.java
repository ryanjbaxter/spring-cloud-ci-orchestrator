package io.github.ryanjbaxter.cireleaseorchestrator.cli;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;

public record OrchestratorArgs(
        Command command,
        String releaseTrainVersion,
        RepoType repoType,
        Set<String> projectsFilter,
        String resumeFrom,
        boolean dryRun,
        String token,
        int pollIntervalSeconds,
        int maxRetries) {

    public enum RepoType {
        OSS, COMMERCIAL
    }

    public enum Command {
        TRIGGER_CI, DEPLOY_DOCS
    }

    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 45;
    private static final int DEFAULT_MAX_RETRIES = 1;
    private static final Command DEFAULT_COMMAND = Command.TRIGGER_CI;

    public static OrchestratorArgs parse(ApplicationArguments args) {
        String commandRaw = firstValue(args, "command");
        Command command = DEFAULT_COMMAND;
        if (commandRaw != null && !commandRaw.isBlank()) {
            command = switch (commandRaw.toLowerCase(Locale.ROOT)) {
                case "trigger-ci" -> Command.TRIGGER_CI;
                case "deploy-docs" -> Command.DEPLOY_DOCS;
                default -> throw new IllegalArgumentException(
                        "--command must be 'trigger-ci' or 'deploy-docs', got '" + commandRaw + "'");
            };
        }

        String releaseTrainVersion = requireOption(args, "release-train-version");

        String repoTypeRaw = requireOption(args, "repo-type");
        RepoType repoType = switch (repoTypeRaw.toLowerCase(Locale.ROOT)) {
            case "oss" -> RepoType.OSS;
            case "commercial" -> RepoType.COMMERCIAL;
            default -> throw new IllegalArgumentException(
                    "--repo-type must be 'oss' or 'commercial', got '" + repoTypeRaw + "'");
        };

        Set<String> projectsFilter = null;
        String projectsRaw = firstValue(args, "projects");
        if (projectsRaw != null && !projectsRaw.isBlank()) {
            projectsFilter = Arrays.stream(projectsRaw.split(","))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        String resumeFrom = firstValue(args, "resume-from");
        if (resumeFrom != null) {
            resumeFrom = resumeFrom.strip();
        }

        boolean dryRun = args.containsOption("dry-run");
        String token = firstValue(args, "token");

        int pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;
        String pollIntervalRaw = firstValue(args, "poll-interval-seconds");
        if (pollIntervalRaw != null && !pollIntervalRaw.isBlank()) {
            pollIntervalSeconds = Integer.parseInt(pollIntervalRaw.strip());
        }

        int maxRetries = DEFAULT_MAX_RETRIES;
        String maxRetriesRaw = firstValue(args, "max-retries");
        if (maxRetriesRaw != null && !maxRetriesRaw.isBlank()) {
            maxRetries = Integer.parseInt(maxRetriesRaw.strip());
            if (maxRetries < 0) {
                throw new IllegalArgumentException("--max-retries must not be negative, got " + maxRetries);
            }
        }

        return new OrchestratorArgs(
                command, releaseTrainVersion, repoType, projectsFilter, resumeFrom, dryRun, token, pollIntervalSeconds,
                maxRetries);
    }

    private static String requireOption(ApplicationArguments args, String name) {
        String value = firstValue(args, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required");
        }
        return value;
    }

    private static String firstValue(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            return null;
        }
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
