# What happens after a business function returns?

This companion to [the first polyglot probe](../polyglot-v0/README.md) fixes the
candidate policy choices that a future language binding must obey. It is a contract
exercise for [contracts #52](https://github.com/The-Pipeline-Framework/pipelineframework-contracts/issues/52),
not a usable Node/Python runtime. Results, source comparisons and outstanding
implementation evidence belong in that issue.

Start with `b-budget-one` in [vectors.json](vectors.json): A has already produced 4.
B fails once, receives **the same 4** on its second attempt, then succeeds. C runs
once. The expected calls are A: 1, B: 2, C: 1. A's retry budget is two, B's is one,
and C's is zero; B's failure must not spend A's budget or invoke A again.

The three consumers calculate those observations independently. Their input is an
ordered list of events **already accepted by a hypothetical shell**. `dispatch`
marks an invocation; `return`, `failure` and `cancel` exercise settlement. This is
a deterministic model of policy ordering, not live asynchronous execution, a
thread-race test, networking, durable recovery or a production Java adapter.

## Run it

From the repository root:

```sh
node conformance/polyglot-policy-v1/consumer.mjs
python3 conformance/polyglot-policy-v1/consumer.py
python3 conformance/polyglot-policy-v1/run.py
```

Node and Python run independently without a JVM or coordinator. The combined runner
uses the same contributor prerequisites and isolated Java build as the first probe.
Use `--skip-build` after those Java artifacts have been built. It compiles the Java
consumer, runs all three against the same corpus, then corrupts fixed expected
answers to check that each consumer detects disagreement. A crash does not count:
the process must report the exact named assertion and exit normally with failure.

The Java consumer uses Jackson to read fixture JSON; its policy model does **not**
call `PipelineRunnerCore`. Matching model answers do not show that released Java
runtime behaviour has changed. The first probe's Java-backed serialization and
signature checks remain separate and unchanged.

## The chosen mapping

| Boundary | Rule |
| --- | --- |
| Retry | Only a valid `TRANSIENT` failure can retry under the admitted `LOCAL_SHELL` owner and that step's remaining budget. `retryable` must agree with the known code; it does not grant authority. Exhaustion retains the last complete failure envelope. |
| Other failures | `BUSINESS_REJECTION` and `UNCLASSIFIED` are terminal. A caught, unrecognised host exception becomes `UNCLASSIFIED` with the fixed message `Unclassified business function failure`. Class names, messages and stacks do not cross this boundary. |
| Structured failure input | Missing/wrongly typed fields, unknown codes, or inconsistent owner, step identity or retry eligibility reject with distinct diagnostics. A valid structured `UNCLASSIFIED` is known and terminal. |
| Policy admission | An independently enabled legacy retry or recovery owner rejects before A. This does not change existing Java defaults or authorise wrapping them with another retry loop. |
| Result | `VALUE` carries an explicit `value` field. Untagged null, an implicit return and a missing value reject. Tagged null requires an explicit nullable boundary declaration. `EMPTY` is distinct but unsupported by this unary research profile. |
| Cancellation | The first accepted **terminal** event wins. A successful intermediate return and a retry-eligible failure with budget remaining are not terminal. Cancellation before B dispatch suppresses B. Cancellation after B starts cannot undo its invocation; it suppresses later work and delivery. |
| Observation versus delivery | Cancellation produces one shell observation and no application value/failure delivery. An earlier committed completion or terminal failure retains its winner. These fixtures do not promise a callback after a host subscriber unsubscribes. |
| Context | Exactly `executionId` and `traceId`, both strings with at least one character other than ASCII space, tab, CR or LF. Bind an immutable snapshot explicitly; do not fall back to ambient values. No credentials, provider handles or coordinator authority. |

`schemaVersion: 1` versions this fixture document. The profile name
`tpf-polyglot-unary-policy/1` deliberately differs from `tpf-polyglot-probe/0`:
the first probe's exhaustion/result conventions have not silently changed. This is
**not** a production protocol version or a supported worker capability advertisement.
Every consumer checks the fixed required case IDs, including missing/duplicate rows.
For this finite model, each step budget is exactly 0, 1 or 2 retries. Configuration
numbers compare by exact numeric value (`1.0` is schema version 1, `2.0` is budget
2); booleans, strings, fractions and numbers rounded into those values reject.
This finite test bound does not prescribe a production retry limit.

Canonical numeric, presence, type identity and hashing rules remain with the first
probe and compiler-owned contracts. This companion checks the outcome tag, payload
presence and null distinction; it does not introduce another canonical value codec.
Its example payloads are small integers. Nullable whole-result declaration is an
explicit hypothetical binding in this corpus, not a claim that current DSL/compiler
projection supports nullable roots.

## Read the code

- [vectors.json](vectors.json) is the machine-readable contract corpus: inputs and
  fixed expected answers, including attempts, materialised inputs, terminal winner,
  observations and delivery. It is not populated by running a consumer.
- [consumer.py](consumer.py), [consumer.mjs](consumer.mjs) and
  [PolicyConsumer.java](java/PolicyConsumer.java) independently implement the mapping.
  They throw/catch a real host exception for the unclassified case. Cancellation
  ordering otherwise comes from the supplied event sequence.
- [run.py](run.py) shares only Java build/classpath plumbing and the established
  mutation-rejection guard with v0. Its mutations challenge each changed axis.
- [test_runner.py](test_runner.py) checks that those mutations preserve their inputs
  and that missing/duplicate cases really challenge corpus membership.

The context check binds two invocations, mutates the callers' inputs, then reads the
snapshots in reverse order. It attempts handler mutation between attempts and checks
that both observations retain the original identity. This is a deterministic
isolation test, not proof of production context propagation.

This increment does not narrow the eventual requirement for streaming, Connectors,
Await recovery or equivalent LOCAL workflows. Production adapter admission,
cancellation mechanics, actual host context propagation and their integration with
existing Java execution still need qualification. No runtime implementation is
scheduled by these fixtures.
