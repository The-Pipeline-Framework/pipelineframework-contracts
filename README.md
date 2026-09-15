# The Pipeline Framework Contracts

This repository publishes stable semantic and runtime contract artifacts shared by the TPF compiler, customer-facing runtimes, and future infrastructure workers. The first artifact is `org.pipelineframework:pipelineframework-semantic-model`.

The semantic model has no production dependency on other TPF artifacts or a framework runtime. Build and test it with `./mvnw clean verify -Dmaven.repo.local="$PWD/.m2/repository"`.

During cutover, the monorepo remains the publication owner until this repository has published and verified the same Maven coordinate. Consumers should use a released artifact rather than a second source copy; the two repositories must never publish the same version concurrently.
