# The Pipeline Framework Contracts

This repository publishes stable semantic and runtime contract artifacts shared by the TPF compiler, customer-facing runtimes, and future infrastructure workers. Its reactor contains `org.pipelineframework:pipelineframework-api`, `org.pipelineframework:pipelineframework-semantic-model`, and `org.pipelineframework:pipelineframework-runtime-serialization`.

The API owns portable authored compiler discovery annotations, including `@PipelineStep`, and generated-name contracts. It has no production dependencies. The semantic model has no production dependency on other TPF artifacts or a framework runtime. Runtime serialization depends only on the semantic model plus Jackson and protobuf, and keeps `PipelineJson` in its existing Java package. Build and test the reactor with `./mvnw clean verify -Dmaven.repo.local="$PWD/.m2/repository"`.

The `pipelineframework-contracts-parent` POM is published only so the library POMs remain resolvable; applications do not depend on it directly.

This repository is the sole publisher of these contract coordinates. During consumer cutover, the monorepo temporarily retains non-deployable source mirrors in its reactor; those mirrors must be removed once consumers resolve the published artifacts. The two repositories must never deploy the same coordinate concurrently.
