# What this polyglot probe does

This directory asks a small question before TPF builds native runtimes: **can Java,
Node and Python agree on the data and execution rules at the language boundary?**
It contains three test programs and one shared set of examples. It does not contain
an application scaffold or a usable Node/Python TPF runtime.

The work belongs to [contracts #52](https://github.com/The-Pipeline-Framework/pipelineframework-contracts/issues/52).
[Draft ADR-0088](https://github.com/The-Pipeline-Framework/pipelineframework/pull/1082)
explains who would own each responsibility between business function A returning
and business function B starting. [EVIDENCE.md](EVIDENCE.md) records what was actually
run, the source revisions, review findings and remaining gaps.

## Start with one example

The functions in each language do the same tiny calculation:

```text
input 3 → A adds 1 → value 4 → B doubles it → result 8
```

The test shell calls A and B asynchronously in that language's process. One case
makes A fail twice before succeeding. With two allowed retries, the expected answer
is **three calls to A, one call to B, result 8**. If A fails permanently or cancellation
wins before B starts, B must not run. Other cases inject malformed failure data to
check that the shell rejects it before deciding whether to retry.

This is a deliberately handwritten test shell. It does not load a pipeline compiled
from DSL, and it is not the production executor we might eventually build. Its value
is finding contradictions in the proposed contract cheaply, before building that executor.
For example, review found that Python shortened small decimals into exponent notation,
Java overflowed a 32-bit result, and different parsers erased different numeric spellings.

### What are “vectors”, “consumers” and “mutations”?

- A **vector** is a test example: input plus its independently specified expected answer.
  All three languages read the same `vectors.json`.
- A **consumer** is a test program that reads those examples and checks its own result.
  Java calls existing TPF serialization/signature classes where available; Node and Python
  implement the proposed checks independently, using their standard libraries.
- A **mutation** deliberately corrupts one expected answer. Changing the retry example's
  expected A count from three to two must make every consumer fail at that particular check.
  A process crash, timeout or failure at another check does not count as success.

The number of passing assertions is useful for spotting accidental changes to the harness.
It is not a percentage of TPF implemented or a language-parity score.

## Run it and read the result

For a quick look, run just Node or Python from the repository root. Neither command
starts Java, a coordinator or a service:

```sh
node conformance/polyglot-v0/consumer.mjs
python3 conformance/polyglot-v0/consumer.py
```

For all three languages, use JDK 25 (matching CI), Node 22.23 or later, and Python 3.11
or later. These are contributor tool prerequisites, not a production support matrix;
see the evidence report for versions actually exercised.

```sh
python3 conformance/polyglot-v0/run.py
```

The runner first builds the existing Java serialization/protocol modules and their
prerequisites, including their ordinary tests, using this worktree's `.m2/repository`.
It then compiles the test-only Java consumer into `target/polyglot-v0`, runs all three
consumers against the shared file, and runs the deliberate mutations.

If the Java artifacts have already been built, skip that build:

```sh
python3 conformance/polyglot-v0/run.py --skip-build
```

Expect one JSON `PASS` result per language, then a `PASS` result with
`mutationChecks: 18`: six changes, each checked by three languages. Those changes
cover null versus absence, numeric precision, retry counts, the signed body's digest,
and boolean/fractional schema versions. The runner checks exit status **and** a stable
`CONFORMANCE_MISMATCH:<check-id>` line for each expected rejection.

A normal consumer failure names the check, such as `numbers/large-candidate/exact`,
then explains the mismatch. Find that row in `vectors.json` and the corresponding
named check function in the consumer. Fix the implementation or propose a contract
change; do not merely change the expected answer until the test passes.

The standalone consumers accept an optional corpus path; Java also defaults to
`conformance/polyglot-v0/vectors.json` when launched from the repository root with
its compiled classpath. The combined runner accepts `--vectors PATH` and resolves
relative paths against the directory where you invoked it. All corpus files use UTF-8.

The runner itself has focused regression tests, including checks that crashes cannot
masquerade as successful mutation rejections:

```sh
python3 -B -m unittest discover -s conformance/polyglot-v0 -p 'test_*.py'
```

## Where to read the code

| File | Read it for |
| --- | --- |
| [vectors.json](vectors.json) | The shared inputs and fixed expected answers; start with `executions` / `retry` |
| [consumer.mjs](consumer.mjs) | Node's independent checks and Promise-based A/B probe |
| [consumer.py](consumer.py) | Python's independent checks and asyncio-based A/B probe |
| [PolyglotProbeConsumer.java](java/PolyglotProbeConsumer.java) | Java's checks, existing TPF API calls and CompletableFuture-based probe |
| [run.py](run.py) | Build/launch commands and deliberate corruption of expected answers |
| [test_runner.py](test_runner.py) | The runner's rejection-evidence and path-resolution rules |
| [EVIDENCE.md](EVIDENCE.md) | Results, scope, provenance and unresolved qualification gaps |

Each consumer follows the same groups: values, identity/signatures, admission,
failures, execution and context. The implementations remain separate so that one
language cannot hide another's disagreement by reusing its calculation.

## The proposed rules being tested

`schemaVersion: 1` versions the fixture document. `profile: tpf-polyglot-probe/0`
names this provisional research profile. Neither changes an existing production wire
protocol. Unknown profiles/capabilities fail rather than silently running a reduced pipeline.

| Group | Question the examples answer |
| --- | --- |
| Presence | Does a missing field remain different from explicit null? Are false, zero and empty values retained? Are requiredness and nullability checked separately? |
| Numbers and unions | Are exact values and declared variants preserved without host-language rounding or implicit coercion? |
| Bytes and signatures | Do all consumers hash the same bytes and construct the same authenticated request preimage? |
| Admission | Do unsupported capabilities, incompatible schema/type identity and conflicting policy stop invocation before A runs? |
| Failures and execution | Are malformed/unknown failures rejected before retry? Are retry counts correct, and is B suppressed after terminal failure or cancellation? |
| Context | Do overlapping A/B invocations see only their own execution/trace identity, with prohibited authority fields rejected? |

### Numeric rules are explicit, not whatever a parser happens to do

Raw data integers, including the `count` union member, use the range
−9,007,199,254,740,991 to +9,007,199,254,740,991 and an integer spelling. The probe
rejects `1.0`, `1e0` and `-0` in that position. Union examples carry their raw value
as `valueJson`, so a JSON parser cannot erase the spelling before it is checked.
A `label` member instead requires a JSON string.

Exact integer/decimal **string atoms are a candidate representation**, not a new
accepted encoding for Java canonical types. The grammar is ASCII and must match the
whole string:

```text
integer: -?(0|[1-9][0-9]*)
decimal: -?(0|[1-9][0-9]*)\.[0-9]+
```

Negative zero, exponent notation, nonfinite values and whitespace reject. Decimal
scale/trailing zeros survive, including `0.0000000`. Integer strings test arbitrary
precision lexical preservation, not canonical int64 range validation or arithmetic.
The long-integer example must not depend on Python's integer-conversion digit limit.

Configuration numbers have a separate rule: schema version and retry limit compare
by exact numeric value. Thus `1.0` is version 1 and `2.0` is a retry limit of 2;
booleans, numeric strings and fractional values are not substitutes. This distinction
is intentional and tested, not an accidental difference between languages.
The comparisons retain the source number's precision: `1.0000000000000001` must not
round into version 1. Node uses JSON parsing's source-token support, Python uses
Decimal, and Java uses BigDecimal. The mutation runner also preserves numeric
spellings when copying the corpus, so an unrelated rounding change cannot hide the
check it intends to challenge.

### Existing Java contracts versus proposed bindings

Java uses `PipelineJson`, `CanonicalFieldValue` and `TransitionWorkerSignature`
directly. Signature paths follow Java's existing blank-path rule, including Unicode
whitespace distinctions, rather than each language's `trim` convention. The fixture
secret is public test data. Hashes cover exact UTF-8 bytes, not an object's incidental
printed form; no Unicode normalisation or new compiler contract-hash algorithm is introduced.

The Node/Python value checks, failure records and admitted profile are candidate
bindings. Presence validation in these tests does not replace generated production
constructors. No consumer resolves Java class names, provider credentials or coordinator
stores from business data. Nonce checks use a test set, not a durable authorisation service.

## What this proves—and what it does not

The LOCAL examples really call asynchronous functions, propagate failures and observe
context within each language process. The `remote-policy-history` and `redelivery-history`
examples use those same local functions to **simulate** ownership and attempt counts.
They open no sockets and do not exercise a coordinator. Three per-step attempts on each
of two coordinator deliveries could mean six invocations: this is not exactly-once execution.

The corpus does not prove compiler generation, native artifact loading, real REST/gRPC
workers, tenant authorisation, Query capture, Command effects, streaming backpressure,
Await restart or deployment. It does not implement a Provider SDK or port the coordinator.
Unsupported capabilities are explicitly rejected by this probe, not removed from TPF's
requirements. **Unary is not the product MVP: streaming, Connectors and durable Await
remain required.**

The next bounded gate is review of the proposed contract, then compiler-owned projection
consumed by an examples-owned native LOCAL proof. Any extension must name its owner,
revise the shared profile and update every affected language's positive/negative examples.
No further implementation, production runtime choice or delivery milestone is scheduled
by this work.
