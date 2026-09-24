# The Pipeline Framework Contracts

This repository publishes the framework-neutral contracts shared by TPF applications, the compiler, runtime
integrations, Connectors, and separately operated execution infrastructure.

Published artifacts:

- `org.pipelineframework:pipelineframework-api` — authored compiler discovery surfaces such as `@PipelineStep`;
- `org.pipelineframework:pipelineframework-semantic-model` — canonical compiler and Pipeline contract model;
- `org.pipelineframework:pipelineframework-dsl` — YAML and composition configuration;
- `org.pipelineframework:pipelineframework-runtime-api` — customer-authored runtime programming contracts;
- `org.pipelineframework:pipelineframework-runtime-core` — portable execution abstractions;
- `org.pipelineframework:pipelineframework-runtime-serialization` — canonical runtime serialization;
- `org.pipelineframework:pipelineframework-runtime-protocol` — transition-worker and execution envelopes;
- `org.pipelineframework:pipelineframework-runtime-spi` — portable provider, store, dispatch, and publication SPIs;
- `org.pipelineframework:representation-provider-api` — representation-provider contracts.

The published parent POM exists so artifact POMs remain resolvable; applications do not depend on it directly.
None of these artifacts loads a Quarkus or Spring runtime implementation. Runtime hosts, worker implementations,
Connector/provider implementations, resolved credentials, and tenant policy belong in their owning integration repositories. Deployment wiring also stays outside this repository.

Build with an isolated Maven repository:

```sh
./mvnw clean verify -Dmaven.repo.local="$PWD/.m2/repository"
```

This repository is the sole publisher of these contract coordinates. During consumer cutover, the monorepo temporarily retains non-deployable source mirrors in its reactor; those mirrors must be removed once consumers resolve the published artifacts. The two repositories must never deploy the same coordinate concurrently.
# Test coverage

`./mvnw clean verify` writes a JaCoCo report for each Maven module under
`<module>/target/site/jacoco/`. CI publishes those reports and execution data
as the `contracts-coverage` artifact for inspection.

Coverage is currently informational. No code is excluded from the report, and
no percentage threshold is enforced until the repository has an observed,
reviewed baseline. Unit tests continue to run through Surefire during `test`;
any future integration or end-to-end tests should remain on their existing
Failsafe lifecycle rather than being folded into Surefire coverage implicitly.
Use the `central-publishing` profile only to sign and deploy the canonical reactor. For the component map and
compatibility policy, see the
[TPF Components and Repositories](https://pipelineframework.org/architecture/components-and-repositories) page.

## System-test candidates

`TPF Candidate Build` runs at the exact pull-request or `main` SHA with read-only permissions and no secrets. It
assigns the reactor a commit-specific `-pr.<number>.<sha12>` or `-main.<sha12>` version, verifies it, and uploads only
the allowlisted Maven coordinates and preliminary metadata. The trusted `TPF Candidate Publish` workflow validates
that build and the current head, publishes those files to this repository's GitHub Packages registry, then
dispatches `tpf-candidate-v1` to the coordination repository. It does not execute project or fork code.

Fork pull requests require `safe-to-system-test`. Configure `SYSTEM_TEST_APP_ID` as a repository variable and
`SYSTEM_TEST_APP_PRIVATE_KEY` as a repository secret for the coordination GitHub App. Candidate publication uses no
Maven Central credentials or GPG key.
