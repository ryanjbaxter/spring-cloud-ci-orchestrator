package io.github.ryanjbaxter.cireleaseorchestrator.ci;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;

/**
 * Dispatches a workflow on a project and polls it to completion. Which workflow file(s) to try is
 * supplied by the caller - {@link #CI_WORKFLOW_CANDIDATES} for the everyday build trigger
 * ({@code ci-release.yml}, then {@code ci.yml}, then {@code ci.yaml}, the same fallback
 * {@code trigger-branch-ci} action already uses, since -internal branches and some release/*
 * branches run ci-release.yml instead of ci.yml), {@link #DOCS_WORKFLOW_CANDIDATES} for the docs
 * deploy trigger (always {@code deploy-docs.yml}, confirmed as the fixed name copied onto every
 * project's docs-build branch per README-deploy-docs.md).
 */
public class WorkflowTrigger {

    public static final List<String> CI_WORKFLOW_CANDIDATES = List.of("ci-release.yml", "ci.yml", "ci.yaml");
    public static final List<String> DOCS_WORKFLOW_CANDIDATES = List.of("deploy-docs.yml");

    private static final String API_BASE = "https://api.github.com";

    private final String token;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WorkflowTrigger(String token) {
        this.token = token;
    }

    public record Dispatched(String workflowFile, long workflowId, Instant dispatchedAt) {
    }

    public Dispatched dispatch(GHRepository repo, String branch, List<String> workflowCandidates) throws IOException {
        for (String candidate : workflowCandidates) {
            GHWorkflow workflow;
            try {
                workflow = repo.getWorkflow(candidate);
            } catch (GHFileNotFoundException e) {
                continue;
            }
            Instant before = Instant.now();
            workflow.dispatch(branch);
            return new Dispatched(candidate, workflow.getId(), before);
        }
        throw new IOException("No " + String.join(", ", workflowCandidates)
                + " workflow found on " + repo.getFullName() + "@" + branch);
    }

    /**
     * GitHub's dispatch API doesn't hand back the run it creates, so this polls for the newest run
     * on the branch, for the dispatched workflow, created no earlier than the dispatch call.
     */
    public GHWorkflowRun findDispatchedRun(GHRepository repo, String branch, Dispatched dispatched,
            Duration timeout, Duration pollInterval) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            for (GHWorkflowRun run : repo.queryWorkflowRuns().branch(branch).list()) {
                if (run.getWorkflowId() == dispatched.workflowId()
                        && !run.getCreatedAt().toInstant().isBefore(dispatched.dispatchedAt())) {
                    return run;
                }
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IOException("Timed out waiting for a new " + dispatched.workflowFile()
                        + " run on " + repo.getFullName() + "@" + branch);
            }
            Thread.sleep(pollInterval.toMillis());
        }
    }

    public GHWorkflowRun.Conclusion awaitCompletion(GHRepository repo, GHWorkflowRun run, Duration pollInterval)
            throws IOException, InterruptedException {
        GHWorkflowRun current = run;
        while (current.getStatus() != GHWorkflowRun.Status.COMPLETED) {
            Thread.sleep(pollInterval.toMillis());
            current = repo.getWorkflowRun(current.getId());
        }
        return current.getConclusion();
    }

    /**
     * Reruns only the run's failed jobs (POST .../rerun-failed-jobs) rather than the whole run.
     * github-api's own {@code GHWorkflowRun.rerun()} only calls the full-rerun endpoint (confirmed
     * by inspecting its bytecode - it POSTs to {@code .../rerun}), and doesn't expose
     * rerun-failed-jobs at all since the {@code createRequest()} builder it's built on is
     * package-private. So this is a small hand-built call to the same endpoint
     * {@code ci-status-report.yml} already uses elsewhere in this ecosystem via {@code gh api}.
     * Waits for {@code run_attempt} to actually increment before polling for completion, since the
     * API can briefly still report the old completed state right after the call.
     */
    public GHWorkflowRun.Conclusion rerunAndAwaitCompletion(GHRepository repo, GHWorkflowRun run, Duration pollInterval)
            throws IOException, InterruptedException {
        long attemptBeforeRerun = run.getRunAttempt();
        rerunFailedJobs(repo.getFullName(), run.getId());

        GHWorkflowRun current = repo.getWorkflowRun(run.getId());
        while (current.getRunAttempt() <= attemptBeforeRerun || current.getStatus() != GHWorkflowRun.Status.COMPLETED) {
            Thread.sleep(pollInterval.toMillis());
            current = repo.getWorkflowRun(run.getId());
        }
        return current.getConclusion();
    }

    private void rerunFailedJobs(String repoFullName, long runId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/repos/" + repoFullName + "/actions/runs/" + runId + "/rerun-failed-jobs"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("rerun-failed-jobs failed for " + repoFullName + " run " + runId
                    + ": HTTP " + response.statusCode() + " - " + response.body());
        }
    }
}
