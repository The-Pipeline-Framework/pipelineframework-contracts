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

Owner-local verification is the first gate. `.github/tpf-system-tests.json` owns the stable contracts suite command.
`TPF Candidate Build` and the trusted publisher create an immutable, commit-specific contracts candidate;
`tpf/system-tests` records downstream evidence on that exact source SHA.

For an ordinary single-repository pull request, use the candidate publisher and singleton system-test path above.
For a coordinated contracts/compiler/runtime change, do **not** wait for participating candidate publishers and do
not merge or publish snapshots one repository at a time. Manually run
[`TPF System Tests — Compatibility Set`](https://github.com/The-Pipeline-Framework/pipelineframework/actions/workflows/system-test-compatibility-set.yml)
with one stable set ID and 2–10 pull-request URLs, one per line. The coordinator pins each PR head and tested merge
commit, builds participating Maven reactors in dependency order into one isolated repository, and runs one product
test over the resulting set. A new commit invalidates that PR's result: rerun the same set ID with the current URLs.
Require the same `tpf/system-tests` success on every participating SHA. Do not substitute snapshots, branch heads,
source checkouts or a composite Maven reactor. See the canonical
[cross-repository system-test runbook](https://github.com/The-Pipeline-Framework/pipelineframework/blob/main/docs/evolve/cross-repository-system-tests.md).

Repository setup requires repository-scoped dispatch credentials. If the workflow exposes them as
`SYSTEM_TEST_APP_ID` and `SYSTEM_TEST_APP_PRIVATE_KEY`, they must belong to a dispatch-only App installed solely on
`pipelineframework`, never the coordinator App. The trusted publisher uses the repository `GITHUB_TOKEN` with
`packages: write`; fork publication additionally requires the
`safe-to-system-test` label. Never expose publication, dispatch or status credentials to owner-suite jobs.

Require a green full train for formal BOM or release promotion.

Always use the repository-local Maven cache:

```sh
./mvnw <goals> -Dmaven.repo.local="$PWD/.m2/repository"
```

Do not introduce Maven profiles except `central-publishing`. It may attach, sign, and deploy artifacts but must not
select another source universe, module graph, or build topology.

Do not commit, push, publish, or change another repository unless explicitly requested.
