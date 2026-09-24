# Contracts Repository Instructions

This repository owns TPF's framework-neutral authored API, semantic model, DSL, runtime API, portable runtime core,
serialization, runtime protocol, runtime SPI, and representation-provider API. Keep these artifacts usable without
loading a Quarkus or Spring runtime implementation.

## Boundary

- Put a type here only when independently owned components must share its meaning or wire identity.
- Keep runtime hosts, worker implementations, Connector/provider implementations, resolved secrets, tenant policy,
  and deployment wiring outside this repository.
- `pipelineframework-api` owns authored compiler discovery surfaces, including `@PipelineStep`; compiler discovery
  must not depend on Quarkus annotations.
- Preserve serialized/protocol compatibility deliberately. Java source compatibility is insufficient when durable
  values or execution envelopes cross process or release boundaries.
- Do not add source mirrors or fallbacks for another TPF repository. Consumers use released artifacts.

## Cross-repository changes

Compiler semantics belong to `pipelineframework-compiler`; Quarkus/Spring behavior belongs to
`pipelineframework-runtime`; external capabilities belong to `pipelineframework-connectors`. Update the canonical
documentation or ADR in `pipelineframework` when a change moves semantic ownership or alters a public protocol.
Use the GitNexus `tpf` group for cross-repository impact and verify findings in the owning worktree.

## Build and publication

Owner-local verification is the first gate. `TPF Candidate Build` and the trusted publisher create an immutable,
commit-specific contracts candidate for the coordination repository; `tpf/system-tests` records downstream
evidence on that exact source SHA. Use a compatibility set for coordinated repository changes, and require a green
full train for formal BOM or release promotion. Keep the stable owner suite command in
`.github/tpf-system-tests.json`.

Always use the repository-local Maven cache:

```sh
./mvnw <goals> -Dmaven.repo.local="$PWD/.m2/repository"
```

Do not introduce Maven profiles except `central-publishing`. It may attach, sign, and deploy artifacts but must not
select another source universe, module graph, or build topology.

Do not commit, push, publish, or change another repository unless explicitly requested.
