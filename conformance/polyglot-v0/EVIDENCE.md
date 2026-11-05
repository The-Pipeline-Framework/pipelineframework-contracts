# Contracts #52 qualification evidence

This report accompanies the provisional `tpf-polyglot-probe/0` corpus. It is evidence for
a bounded contract proposal, not certification of a native runtime or acceptance of the
whole [polyglot epic](https://github.com/The-Pipeline-Framework/pipelineframework/issues/1055).

## Requirements and verification basis

Requirements: [contracts #52](https://github.com/The-Pipeline-Framework/pipelineframework-contracts/issues/52)
as retrieved on 6 November 2026 (last issue edit 1 November), the accepted discussion clarifications,
and the [draft decision](https://github.com/The-Pipeline-Framework/pipelineframework/pull/1082).
The original issue's unary-first increment is preserved as a probe; its later unary-only MVP
suggestion does not remove streaming, Connectors or durable Await requirements.

The lead established these obligations before the final consumer implementation. A separate
reviewer owns adequacy; the consumer author does not approve their own evidence.

| Obligation | Forbidden outcome / challenge | Observable evidence and limits |
|---|---|---|
| C1 Closed profile | Unknown schema/profile/type/capability or conflicting retry policy invokes a function | Admission diagnostics and invocation counts; handcrafted projection, not compiler enforcement |
| C2 Presence | Missing/null/false/zero/empty collapse; required/non-null constraints silently relax | Shared positive/negative objects, round trips, root-absence rejection; Java uses actual `PipelineJson`/`CanonicalFieldValue` |
| C3 Precision and identity | Binary-number loss, implicit hash rendering, unknown union identity | Exact lexical values, known variant/value checks, byte/hash/HMAC preimages and pinned identities; candidate atoms are not deployed codecs or compiler contract hashes |
| C4 Policy | Retry multiplication, B after terminal A/cancellation, wrong attempt count | Actual async LOCAL A/B calls and fixed outcome/count oracles; remote/redelivery cases are policy simulations only |
| C5 Failures | Java exception name becomes native policy; unknown/malformed failure silently retries | Structured failure diagnostics and asynchronous throw/rejection handling; no existing runtime exception reconstruction is replaced |
| C6 Context | Cross-run context leakage or provider/store authority in business data | Overlapping A/B observations compared to corpus expectations; restricted context keys; no sandbox or task-local framework implementation claim |
| C7 Trust and compatibility | Tampered identity/body or repeated nonce admitted by the proposed check | Shared signature and mismatch cases, nonce set probe; no real tenant authorisation, durable nonce store, network worker or HA claim |
| C8 Evidence limits | One language or toy fixture advertised as product parity | Three independent consumers, deliberate wrong null/precision/retry expectations, explicit scope of each case; no generated application/dev-tool workflow claim |

Expected values are committed in the shared corpus and checked by independent language
implementations. Existing signature canonicalisation is additionally checked through the
actual Java protocol class. Tests deliberately challenge missing/null and unsafe-number
distinctions rather than deriving their oracle from the consumer being tested. Mutation
witnesses reject deliberately wrong expectations; they are not historical defect evidence
or a general mutation-score claim.

## Revisions and reproduction

Contracts baseline: `acf6bd2db310462d526695ec65a5479b2c7f134f`.
Runtime evidence refresh: `60dce3e400f51656631230ab9fe910ecb9a6c972` (read-only; no runtime changes).
Root documentation baseline: `c7d4fe20c439f698e3be24cd3d56c0cf5b5f5702`.
Corpus SHA-256: `31d727ca1abcea09811da7222de6b78a966b91d9d8decbcc052d4c6c6b384a44`.

Run the exact candidate commit containing this report. The runner prints the consumer results;
the PR records its tested head. No Maven dependency, production source, published coordinate
or released wire format is modified. Java calls the built baseline classes directly; Node and
Python use only their standard libraries. Their standalone commands do not start a JVM.

```sh
./mvnw -B verify -Dmaven.repo.local="$PWD/.m2/repository" --no-transfer-progress
python3 conformance/polyglot-v0/run.py --skip-build
node conformance/polyglot-v0/consumer.mjs
python3 conformance/polyglot-v0/consumer.py
```

The CI workflow runs the same combined consumer check after canonical Maven verification.
`run.py` without `--skip-build` builds the relevant Java modules first. That contributor-side
Java reference check is separate from the eventual no-local-JVM native developer workflow.

## Results and review

- Full canonical Maven `verify`: passed all ten modules on 6 November 2026 (23.429 seconds).
- Combined consumer run: passed in Java, Node and Python (184, 177 and 177 checks respectively).
  All nine deliberately corrupted null/precision/retry-oracle runs failed as required.
  These counts describe the executed harness, not a coverage or parity score.
- Environment: OpenJDK 25.0.4.1, Node 26.8.2, Python 3.14.6 on macOS. CI records its own versions;
  this report does not claim an untested version matrix.
- Companion documentation: 36 tests passed; VitePress build and Coffee Machine public-content
  audit passed. The decision remains draft.

The initial independent review falsified unused retry-owner and tag-only union checks and
identified self-referential context expectations and simulated Java failure branches. These
were treated as acceptance blockers in the harness, not as production TPF defects.
Final cross-language falsification found a raw union-integer precision mismatch: Java/Python
accepted a value outside Node's safe range. All three now reject it through a shared vector.
The same review also exposed Python's exponent formatting for small fixed-point decimals.
Fixed-point scale is now retained, and common ASCII/full-string numeric grammar is checked
against non-ASCII digits and a trailing newline instead of relying on host regex defaults.
Independent final adequacy/source review: **READY for the bounded contract-proposal/probe PR**.
The reviewer reproduced the three consumer results and nine mutation rejections against the
corpus digest above. This verdict does not qualify production or distributed execution.

GitNexus orientation/context/impact used the contracts index at the exact baseline. Its
managed clone is a different Git repository from the isolated worktree: `detect_changes`
rejects that worktree, and the new probe files are not indexed. No index rebuild was authorised
or performed. Accordingly, changed-symbol and PDG/taint coverage are **unavailable**, not zero
impact. The automatic review gate uses disclosed source review and executable falsification
for this surface. Root docs change analysis succeeded with its existing worktree index,
supplemented by source review for the new ADR.

## Remaining gaps and next bounded gate

This increment does not qualify compiler-generated artifacts, complete canonical type coverage,
deployed remote step/worker bindings, real authentication/tenant policy, streaming backpressure,
Connector capture/effect semantics, Await restart, FUNCTION or production native runtimes.
The proposed portable numeric representation and Java payload/failure migration still need
compiler/runtime agreement. `PipelineInvocationPath` is now part of the Java durable context;
profile 0 rejects that capability instead of silently dropping it.

After contract review, the next bounded gate is compiler-authoritative projection and an
examples-owned native LOCAL consumer journey, retaining generated type checks and explicit
unsupported diagnostics. Streaming, Connector and durable Await proof gates must be met before
declaring a credible product MVP. Detroit and the Java-daemon option remain unselected;
neither gains approval from these probes. No issue is closed, no implementation is scheduled,
and no merge, release publication or deployment is performed by this increment.
