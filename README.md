# ci-release-orchestrator

[![CI](https://github.com/ryanjbaxter/spring-cloud-ci-orchestrator/actions/workflows/ci.yml/badge.svg)](https://github.com/ryanjbaxter/spring-cloud-ci-orchestrator/actions/workflows/ci.yml)

A local Spring Boot CLI that triggers cross-repo Spring Cloud GitHub Actions workflows — CI builds or docs deploys — in dependency order, computed from the projects' real `pom.xml` files rather than a hand-maintained list.

## What it does

Given a release train version, it:

1. Reads the project list and per-project versions from the `jenkins-releaser-config` branch of `spring-cloud/spring-cloud-release-commercial` (used for both OSS and commercial trains).
2. Resolves each project's real development branch for that version.
3. Fetches every project's `pom.xml` files on that branch and derives the dependency graph from `<parent>` references and `*.version` properties
4. Topologically sorts the graph into levels and either prints the plan (`--dry-run`) or triggers each project's workflow, waiting for completion before triggering its dependents, with independent projects in the same level running concurrently.

Two things it can trigger (`--command`):

- **`trigger-ci`** (default) — dispatches each project's CI workflow (`ci-release.yml`, `ci.yml`, or `ci.yaml`, whichever exists) on the branch resolved in step 2.
- **`deploy-docs`** — dispatches `deploy-docs.yml` on each project's fixed `docs-build` branch instead. The dependency order still comes from the real graph in step 3, because a `docs-build` branch's own `pom.xml` is a single-module, dependency-free stand-in (its only cross-reference is a `<parent>` pointing back at its own project) and can't be used to derive real ordering.

## Requirements

- Java 25
- A GitHub token with read access to the target repos (`repo` scope for private/commercial repos) and `actions: write` to trigger workflows. Resolved in this order:
  1. `--token=<token>`
  2. `ORCHESTRATOR_GITHUB_TOKEN` environment variable
  3. `gh auth token` (so if you're already logged in with the GitHub CLI, no extra setup is needed)

## Install

Download the executable jar from the [latest release](https://github.com/ryanjbaxter/spring-cloud-ci-orchestrator/releases/latest):

```bash
gh release download --pattern '*.jar*' -R ryanjbaxter/spring-cloud-ci-orchestrator
sha256sum -c ci-release-orchestrator-*.jar.sha256
java -jar ci-release-orchestrator-*.jar --help
```

Only Java 25 is needed to run it — no Maven toolchain.

## Build

```bash
mvn clean package
```

This produces `target/ci-release-orchestrator-<version>.jar`.

## Usage

```bash
java -jar target/ci-release-orchestrator-<version>.jar --help
```

prints full usage. Summary:

```
java -jar ci-release-orchestrator.jar --release-train-version=<version> --repo-type=<oss|commercial> [options]
```

| Flag | Required | Default | Description |
|---|---|---|---|
| `--release-train-version` | yes | — | Release train version to look up, e.g. `2025.1.3`, `2025.1.3-SNAPSHOT`, `2025.1.3-INTERNAL-SNAPSHOT`. |
| `--repo-type` | yes | — | `oss` (`spring-cloud/<project>`) or `commercial` (`spring-cloud/<project>-commercial`). |
| `--command` | no | `trigger-ci` | `trigger-ci` or `deploy-docs`. |
| `--projects` | no | every project in the properties file | Comma-separated project names to restrict to. |
| `--resume-from` | no | — | Only (re)trigger this project and everything that transitively depends on it; treat everything else as already handled. |
| `--dry-run` | no | `false` | Resolve branches, fetch poms, and print the plan without triggering or waiting on anything. |
| `--token` | no | — | See token resolution order above. |
| `--poll-interval-seconds` | no | `45` | How often to poll a triggered run for completion. |
| `--max-retries` | no | `1` | Automatic retries via GitHub's `rerun-failed-jobs` for a run that doesn't finish successfully. `0` disables retries. |
| `--help` / `-h` | no | — | Print usage and exit (skips starting the app entirely). |

### Examples

Preview the build order for a commercial internal-snapshot train without triggering anything:

```bash
java -jar target/ci-release-orchestrator-<version>.jar \
  --release-train-version=2025.1.3-INTERNAL-SNAPSHOT --repo-type=commercial --dry-run
```

Trigger CI for two projects only:

```bash
java -jar target/ci-release-orchestrator-<version>.jar \
  --release-train-version=2025.1.3 --repo-type=oss \
  --projects=spring-cloud-build,spring-cloud-commons
```

Resume after `spring-cloud-config` failed partway through a run:

```bash
java -jar target/ci-release-orchestrator-<version>.jar \
  --release-train-version=2025.1.3 --repo-type=commercial --resume-from=spring-cloud-config
```

Redeploy docs for every project on a train, in dependency order:

```bash
java -jar target/ci-release-orchestrator-<version>.jar \
  --command=deploy-docs --release-train-version=2025.1.3 --repo-type=oss
```

## How it works

- **`releaser/ReleaserConfigClient`** — fetches and parses `releaser.fixed-versions[project]=version` entries from the properties file.
- **`branch/BranchResolver`** — per-project branch resolution: try `<major>.<minor>.x`, then `<major>.<minor>.x-internal` (common on actively-developed commercial lines), then (OSS only) `main` if its own pom version is genuinely on that line. Commercial repos get no `main` fallback.
- **`graph/PomFetcher`, `graph/PomXml`, `graph/DependencyGraphBuilder`, `graph/TopologicalSorter`** — fetch every `pom.xml` on the resolved branch, parse `<parent>`/`*.version` references with the JDK's built-in DOM parser (no extra XML dependency), and topologically sort into levels via Kahn's algorithm.
- **`orchestration/GraphResolver`** — ties the above together into "which projects, in what order," shared by every command.
- **`orchestration/OrchestrationCommand`** — applies `--resume-from` pruning, prints the plan, and (unless `--dry-run`) drives `ci/OrchestrationCoordinator`.
- **`ci/OrchestrationCoordinator`** — walks the levels, a virtual thread per project per level, blocking a project whenever any of its prerequisites didn't succeed (block-descendants-on-failure).
- **`ci/WorkflowTrigger`** — dispatches the right workflow file, polls for completion, and retries via GitHub's `rerun-failed-jobs` endpoint (a small hand-built HTTP call — the `github-api` library only exposes a full-rerun endpoint, not failed-jobs-only).

## Testing

```bash
mvn test
```

Unit tests cover the pure logic (properties parsing, branch resolution, pom parsing, graph construction, topological sort, arg parsing) with fixtures, using Mockito for the pieces that need a `GitHub`/`GHRepository` collaborator. There are no integration tests against live GitHub; verify end-to-end with `--dry-run` against real data, then a small `--projects=<one low-risk project>` run before trusting it with the full graph.

## Known limitations (v1)

- **Single machine, no persistence.** State lives only for the duration of one run. `--resume-from` re-derives the graph fresh each time rather than reading a saved plan — this is deliberate (GitHub's API is always the source of truth), not a missing feature.
- **Branch alignment isn't guaranteed.** The `<major>.<minor>.x` / `-internal` / `main` resolution covers the common cases observed in this project family, but a project on an unusual branch naming scheme will be reported as a skipped branch-resolution failure rather than guessed at.
- **No unattended/overnight story.** This only runs while a developer's laptop is on, awake, and connected — it's a stepping stone for iterating on the dependency-graph and trigger-and-wait mechanics, not a replacement for a scheduled CI-based orchestrator.
