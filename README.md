# The Pipeline Framework Contracts

This repository publishes stable semantic and runtime contract artifacts shared by the TPF compiler, customer-facing runtimes, and future infrastructure workers. Its current reactor contains `org.pipelineframework:pipelineframework-semantic-model` and `org.pipelineframework:pipelineframework-runtime-serialization`.

The semantic model has no production dependency on other TPF artifacts or a framework runtime. Runtime serialization depends only on the semantic model plus Jackson and protobuf, and keeps `PipelineJson` in its existing Java package. Build and test both with `./mvnw clean verify -Dmaven.repo.local="$PWD/.m2/repository"`.

The `pipelineframework-contracts-parent` POM is published only so the two library POMs remain resolvable; applications do not depend on it directly.

During cutover, the monorepo remains the publication owner until this repository has published and verified the same Maven coordinate. Consumers should use a released artifact rather than a second source copy; the two repositories must never publish the same version concurrently.
