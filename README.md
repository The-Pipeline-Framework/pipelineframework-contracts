# The Pipeline Framework Contracts

This repository publishes stable semantic and runtime contract artifacts shared by the TPF compiler, customer-facing runtimes, and future infrastructure workers. Its reactor contains `org.pipelineframework:pipelineframework-api`, `org.pipelineframework:pipelineframework-runtime-api`, `org.pipelineframework:pipelineframework-semantic-model`, `org.pipelineframework:pipelineframework-runtime-serialization`, `org.pipelineframework:pipelineframework-runtime-core`, `org.pipelineframework:pipelineframework-runtime-protocol`, and `org.pipelineframework:pipelineframework-runtime-spi`.

The API owns portable authored compiler discovery annotations, including `@PipelineStep`, and generated-name contracts. It has no production dependencies. Customer runtime API owns authored reactive service, failure, and await projection contracts; its only production dependency is Mutiny, pinned directly rather than through a Quarkus BOM. It is not the JDK-only worker/customer shared model. The semantic model has no production dependency on other TPF artifacts or a framework runtime. Runtime serialization depends only on the semantic model plus Jackson and protobuf, and keeps `PipelineJson` in its existing Java package. Build and test the reactor with `./mvnw clean verify -Dmaven.repo.local="$PWD/.m2/repository"`.

The `pipelineframework-contracts-parent` POM is published only so the library POMs remain resolvable; applications do not depend on it directly.

`pipelineframework-runtime-core` has been transferred here with its complete source, tests, and service registration. This repository publishes that coordinate only after the monorepo stops deploying its non-deployable source mirror. The snapshot workflow checks the monorepo's current `main` publication manifest and mirror POM before any deploy. The monorepo mirror can be removed after a contracts-repository snapshot is published and its consumers are verified.

`pipelineframework-runtime-protocol` carries the shared transition-worker wire model and protocol resources. It is staged as non-deployable until the monorepo publisher handoff is complete; it depends only on the contracts-owned runtime core and does not load a runtime implementation.

`pipelineframework-runtime-spi` carries framework-neutral provider, store, dispatch, and publication extension contracts shared by runtime integrations. It is staged as non-deployable until the monorepo publisher handoff is complete and does not depend on Quarkus or Spring implementations.

This repository is the sole publisher of these contract coordinates. During consumer cutover, the monorepo temporarily retains non-deployable source mirrors in its reactor; those mirrors must be removed once consumers resolve the published artifacts. The two repositories must never deploy the same coordinate concurrently.

## System-test candidates

The `TPF Candidate Build` workflow runs with read-only permissions and no secrets. It checks out the
exact PR head (or main push), assigns the reactor a commit-specific `-pr.<number>.<sha12>` or
`-main.<sha12>` version, verifies and installs it, then uploads only the ten allowlisted Maven
coordinates and preliminary build metadata. The separate, trusted `TPF Candidate Publish` workflow
validates that build and the current PR head before publishing the allowlisted files to this
repository's GitHub Packages Maven registry. It then creates the final `tpf-candidate-manifest` and
`tpf-candidate-event` artifacts and dispatches `tpf-candidate-v1` to the framework repository. The
publisher validates data from the build artifact and does not execute project or fork code.

Fork pull requests require the `safe-to-system-test` label before candidate publication. Configure
`SYSTEM_TEST_APP_ID` as a repository variable and `SYSTEM_TEST_APP_PRIVATE_KEY` as a repository secret
for a GitHub App installed on `The-Pipeline-Framework/pipelineframework` with Contents write
permission. The package publisher uses the workflow `GITHUB_TOKEN` with Packages write permission; no
Central credentials or GPG key are used by candidate publication.
