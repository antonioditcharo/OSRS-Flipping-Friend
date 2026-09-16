# Supported Components and Build Inventory

## Baseline

- Required base branch: `CoPilot's-Rebuild-from-Main`
- Required base commit: `460c7240afc4c10f747d37f46f789b3433d331cd`
- Inventory date: 2026-09-14
- Gradle modules: root RuneLite plugin, `core`, and `companion`
- Java target: 11
- Gradle wrapper distribution: 8.14

The supplied source bundle identifies branch `claude/plugin-trade-recommendations-profit-a27519` at commit `5ff3fa8`. The current snapshot is therefore a different revision. Package 1.7 removes that embedded source dump because it is generated reference material, not current source.

## Supported production components

1. **RuneLite plugin**: root project. Observes game state and presents guidance.
2. **Shared core**: `core`. Contains client-independent models and optimization rules.
3. **Java companion**: `companion`. Local application service with `com.flippingfriend.companion.CompanionMain` as its entry point.

## Supported production artifacts

| Artifact | Producing task | Expected path |
| --- | --- | --- |
| RuneLite plugin | `jar` | `build/libs/osrs-flipping-friend.jar` |
| Java companion | `:companion:shadowJar` | `companion/build/libs/flipping-friend-companion.jar` |

`companionJar` remains a convenience lifecycle task that delegates to `:companion:shadowJar`; it does not create a third artifact.

## Supported verification commands

- Narrow inventory check: `powershell -File tools/verify-supported-build.ps1`
- Java tests: `gradlew.bat test`
- Supported artifacts: `gradlew.bat jar :companion:shadowJar`
- Full standard check when dependencies are available: `gradlew.bat clean test jar :companion:shadowJar`

## Removed ghost build entry points

The following Gradle tasks referred to classes absent from the current source tree and were removed:

- `daemon` and `daemonJar` -> `com.flippingfriend.daemon.LearningDaemon`
- `:companion:replayJar` -> `com.flippingfriend.companion.ReplayMain`
- `:companion:trainerJar` -> `com.flippingfriend.companion.ModelTrainer`
- `:companion:monitorJar` -> `com.flippingfriend.companion.LearningMonitor`

The build invocations in `tools/apply-update.ps1` and `tools/pipeline-check.ps1` now request only supported artifacts. The Python ML launcher was retired in Package 1.4, the unsupported dashboard launcher in Package 1.5, and the obsolete background-learning launcher in Package 1.8.

## Non-production or pending classification

The following remain in the repository but are not declared supported production artifacts by this package:

- `ml-forecaster`
- the `backtest` Gradle task, which points to the existing `Backtester` class and is a developer verification tool, not a production artifact

These remaining paths require evidence-driven retirement or containment in later Phase 1 packages.

## Ports found during inventory

- Java companion: `127.0.0.1:37777`

## Baseline execution result

The agent attempted `gradlew tasks --all`, `gradlew test`, and `gradlew clean jar :companion:shadowJar`. All three stopped before Gradle execution because the isolated environment could not resolve `services.gradle.org` to download Gradle 8.14. Consequently, this package is **not agent-verified** by compilation or tests. The supplied verification script must pass in the repository environment before commit or merge.

## Preserved data

This package does not change schemas, databases, credentials, caches, journals, positions, model history, or local configuration.
