# The Pipeline Framework Contracts

This repository publishes stable semantic and runtime contract artifacts shared by the TPF compiler, customer-facing runtimes, and future infrastructure workers. Its reactor contains `org.pipelineframework:pipelineframework-api`, `org.pipelineframework:pipelineframework-runtime-api`, `org.pipelineframework:pipelineframework-semantic-model`, and `org.pipelineframework:pipelineframework-runtime-serialization`.

The API owns portable authored compiler discovery annotations, including `@PipelineStep`, and generated-name contracts. It has no production dependencies. Customer runtime API owns authored reactive service, failure, and await projection contracts; its only production dependency is Mutiny, pinned directly rather than through a Quarkus BOM. It is not the JDK-only worker/customer shared model. The semantic model has no production dependency on other TPF artifacts or a framework runtime. Runtime serialization depends only on the semantic model plus Jackson and protobuf, and keeps `PipelineJson` in its existing Java package. Build and test the reactor with `./mvnw clean verify -Dmaven.repo.local="$PWD/.m2/repository"`.

The `pipelineframework-contracts-parent` POM is published only so the library POMs remain resolvable; applications do not depend on it directly.

`pipelineframework-runtime-core` has been transferred here with its complete source, tests, and service registration. This repository publishes that coordinate only after the monorepo stops deploying its non-deployable source mirror. The snapshot workflow checks the monorepo's current `main` publication manifest and mirror POM before any deploy. The monorepo mirror can be removed after a contracts-repository snapshot is published and its consumers are verified.

This repository is the sole publisher of these contract coordinates. During consumer cutover, the monorepo temporarily retains non-deployable source mirrors in its reactor; those mirrors must be removed once consumers resolve the published artifacts. The two repositories must never deploy the same coordinate concurrently.
