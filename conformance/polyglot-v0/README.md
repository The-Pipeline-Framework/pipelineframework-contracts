# Polyglot contract qualification probe, version 0

This is a **handcrafted, non-production candidate** for contracts [#52](https://github.com/The-Pipeline-Framework/pipelineframework-contracts/issues/52), supporting [draft ADR-0088](https://github.com/The-Pipeline-Framework/pipelineframework/pull/1082). It qualifies a first finite unary probe. It is not a product MVP, compiler output, native runtime implementation, remote compatibility test, or a ceiling on the native LOCAL equivalence requirement.

## Run

From the repository root, with JDK 21+, Node 22+ and Python 3.11+:

```sh
python3 conformance/polyglot-v0/run.py
```

The runner invokes the existing Maven reactor for serialization/protocol and their prerequisites with the isolated `.m2/repository`, including their ordinary tests. After an existing full `./mvnw verify -Dmaven.repo.local="$PWD/.m2/repository"`, use:

```sh
python3 conformance/polyglot-v0/run.py --skip-build
```

The Java consumer compiles only into `target/polyglot-v0`. Its classpath uses the four existing module class directories and the five exact Jackson/protobuf coordinates declared in the root POM; it does not scan for jars or alter artifact dependencies. Java consumes the existing `PipelineJson`, `CanonicalFieldValue`, and `TransitionWorkerSignature` APIs directly. All consumer source is test-only. Node and Python run independently, without Java:

```sh
node conformance/polyglot-v0/consumer.mjs
python3 conformance/polyglot-v0/consumer.py
```

Each executable accepts an optional corpus path. The runner uses `--vectors PATH` to select one shared JSON file. Assertion failures produce a nonzero exit. Nine additional runs must reject deliberately changed null-state, exact-integer and retry-count oracles, one mutation per language. These are discrimination checks, not a second source of truth.

## Corpus and candidate rules

`vectors.json` is the sole fixed oracle. `schemaVersion: 1` describes this fixture format; `profile: tpf-polyglot-probe/0` is the provisional execution-profile identifier. Consumers reject unknown corpus versions/profiles. None of this introduces a new production wire protocol.

| Section | Input and oracle | Rule exercised |
| --- | --- | --- |
| `presence` | JSON text, optional/nullable flags, expected state and diagnostic | All four optional/nullable combinations; absent, null, false, zero, empty collection, nested Unicode values; round trip without default creation. Java uses the real canonical wrapper/mapper; Node/Python emulate the candidate object-field rule. Root ABSENT rejection is checked separately. |
| `numbers` | kind, original lexeme, expected exact text, diagnostic | Raw JSON integers limited to ±(2^53−1). Unsafe integers reject. Arbitrary-precision integers and exact decimals use **candidate string atoms**, not an accepted Java canonical-wire encoding. This probes arbitrary-precision lexical handling, not canonical int64 range validation. Candidate integer grammar is `-?(0|[1-9][0-9]*)`; decimal grammar is `-?(0|[1-9][0-9]*)\.[0-9]+`. Negative zero rejects in both, including decimal `-0.00`; exponent notation and nonfinite decimal atoms reject. Decimal coefficient/scale and trailing zeros survive. Raw decimal, nonfinite values and negative zero explicitly reject in this probe. |
| `unions` | tag, value, diagnostic | Closed `count`/`label` logical union, including known tag with wrong value type and unknown Java-looking tag. No dynamic class loading. |
| `bytes` | base64 bytes and fixed SHA-256 | Exact UTF-8 bytes; whitespace changes identity. Unicode is preserved without normalization. Object rendering is never a digest preimage. |
| `signature` | secret, method, path, timestamp, nonce, exact body, SHA-256, preimage and fixed HMAC | Reuses the existing transition-worker POST vector and Java signing API. Newline-delimited canonicalization and normalized path match existing semantics. Secret is a public fixture value. |
| `pins`, `authMutations` | expected pipeline/contract/release/artifact/digest identities; changed request fields | Exact identity comparison and cryptographic tamper checks; separate nonce-set replay rejection. This models checks, not a network authenticator or durable nonce store. |
| `admission` | fixture schema version, profile, one `pure-unary` capability, logical payload type, retry owner/limit | Unknown/missing capability, profile, payload type, version or conflicting policy rejects before the gated test invocation. Rejected cases have zero invocations. |
| `failures` | portable data envelope and diagnostic | Closed failure codes, message, retry flag, owner and step identity; malformed/unknown envelopes reject without loading failure classes. These candidate atoms are separate from existing Java failure reconstruction. |
| `executions` | input, induced A failures, retry limit, terminal/cancellation flags, delivery count, expected outcome/counts | Actual asynchronous A adds one; B doubles. Failed asynchronous A stages are normalized by each test shell. Terminal A/cancellation prevents B. Cancellation during A suppresses its late result. |
| `contexts`, `forbiddenContextKeys` | independent expected execution/trace IDs and function results | Two interleaved executions record actual context inside A and B and compare independently pinned expectations. Closed business context accepts execution/trace IDs; provider credentials, secrets, coordinator store, tenant policy and retry ownership reject. |

Presence policy checks enforce this corpus's nullable/optional flags separately from the Java wrapper decode. The generated production constructor's schema validation remains authoritative; these checks do not replace it. Logical payload/union/failure tags are candidate allowlists, never FQCN resolution. Unicode normalization, generic JSON object canonicalization, deadlines and floating-point formats are not invented here.

## Retry and authority

For the actual local async probe, the invocation shell owns retry with `retryLimit=2`, hence at most **three** A attempts and one B invocation after A succeeds. Nonretryable rejection uses one A attempt and zero B invocations. Cancellation before B, including cancellation while A is pending, yields zero B invocations. Cancellation is cooperative; no interruption or rollback claim is made.

The `remote-policy-history` and `redelivery-history` cases are explicitly **policy simulations only**, using the same local functions. They do not open sockets or admit coordinator work. They illustrate a caller that sends once and a callee invocation shell that may attempt A three times, plus two coordinator deliveries that can repeat three attempts each (six A attempts total). The caller does not add logical business retries. Direct remote response loss has unknown outcome and cannot trigger automatic resubmission. There is no exactly-once invocation promise.

`retryOwner` is validated as a closed selection on admitted local fixtures. Remote/coordinator history labels describe the outer simulated boundary, not an enabled execution profile. The production coordinator, worker client/server, nonce state and failure classification remain their existing semantic owners.

## Evidence limits and extension gate

This fixture does not interpret YAML, load generated native artifacts, access providers, run a distributed transition, implement Await/streaming, or exercise durable recovery. Unsupported capabilities explicitly reject in this profile, including Query, Command, cache, persistence, queue-async, invocation-path and Await occurrence-path semantics; these remain wider roadmap obligations. Application API, step RPC and transition-worker RPC are distinct; reusing a transition-worker signature vector does not make the probe a step RPC implementation.

Promotion requires the compiler-owned projection, native loader/runtime, declared layouts and developer journey to pass their own acceptance. An extension must revise the shared profile, specify owner/policy changes, update all affected language consumers and fixed positive/negative oracles, and obtain shared-contract review. Hosted Query requires separate authentication, credential, capture/replay and host evidence. Provider SDK and coordinator ports remain outside this probe.
