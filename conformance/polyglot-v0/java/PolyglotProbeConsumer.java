import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.pipelineframework.config.pipeline.PipelineJson;
import org.pipelineframework.orchestrator.TransitionWorkerSignature;
import org.pipelineframework.type.CanonicalFieldValue;

/**
 * Independent, test-only consumer of the handcrafted candidate corpus.
 *
 * <p>The wrapper, JSON mapper and signer are existing production APIs. Admission, tagged values,
 * failure allowlists and the A/B invocation shell below are deliberately local probe rules. This
 * executable neither loads generated native artifacts nor implements a production runtime.
 */
public final class PolyglotProbeConsumer {
    private final ObjectMapper mapper = PipelineJson.mapper()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final JsonNode corpus;
    private int checks;

    record Field(CanonicalFieldValue<JsonNode> value) {}
    record ExecutionContext(String executionId, String traceId) {}
    record Failure(String code, String message, boolean retryable, String owner, String stepId) {}
    record Observation(String step, String executionId, String traceId) {}
    record Outcome(int aAttempts, int bAttempts, int callerAttempts, String outcome, long result) {}
    record Numeric(String diagnostic, String exact) {}

    /** Carries data, including intentionally malformed fixtures, across an asynchronous boundary. */
    static final class InvocationFailure extends RuntimeException {
        final JsonNode envelope;

        InvocationFailure(JsonNode envelope) {
            super("Asynchronous probe failure");
            this.envelope = envelope;
        }
    }

    /**
     * Opens only after every context probe has entered A. The timeout diagnoses a broken test
     * barrier; it does not add deadline semantics to the candidate execution profile.
     */
    static final class StartBarrier {
        private final int participants;
        private final AtomicInteger arrivals = new AtomicInteger();
        private final CompletableFuture<Boolean> ready = new CompletableFuture<>();

        StartBarrier(int participants) {
            this.participants = participants;
            ready.orTimeout(10, TimeUnit.SECONDS);
        }

        /** Records arrival before releasing all participants together. */
        void arriveAndAwait() {
            if (arrivals.incrementAndGet() == participants) {
                ready.complete(true);
            }
            ready.join();
        }
    }

    /** Reads configuration numbers exactly, without narrowing fractions to integer versions. */
    private PolyglotProbeConsumer(Path path) throws Exception {
        corpus = mapper.readTree(Files.readString(path));
        equal(exactNumber(corpus.path("schemaVersion"), 1), true, "corpus/schemaVersion");
        equal(text(corpus, "profile"), "tpf-polyglot-probe/0", "corpus/profile");
    }

    /** Emits a check-owned marker so a mutation rejection cannot be confused with a crash. */
    private synchronized void equal(Object actual, Object expected, String id) {
        if (!Objects.equals(actual, expected)) {
            System.err.println("CONFORMANCE_MISMATCH:" + id);
            throw new AssertionError(id + ": " + actual + " != " + expected);
        }
        checks++;
    }

    private String text(JsonNode value, String field) {
        return value.path(field).asText();
    }

    /** Numeric configuration accepts equivalent JSON spellings, but never booleans or strings. */
    private boolean exactNumber(JsonNode value, long expected) {
        return value.isNumber() && value.decimalValue().compareTo(BigDecimal.valueOf(expected)) == 0;
    }

    /** Preserves the wrapper's three distinct field states before applying fixture policy. */
    private String state(CanonicalFieldValue<?> field) {
        return field.isAbsent() ? "ABSENT" : field.isNull() ? "NULL" : "VALUE";
    }

    /** Integer candidate tokens are lexical: decimal, exponent and negative-zero forms reject. */
    private boolean integerToken(String lexeme) {
        return lexeme.matches("-?(0|[1-9][0-9]*)") && !lexeme.equals("-0");
    }

    /** Candidate string atoms are probe encodings, not a new canonical production wire format. */
    private Numeric number(JsonNode vector) {
        String kind = text(vector, "kind");
        String lexeme = text(vector, "lexeme");
        return switch (kind) {
            case "json-safe-integer" -> {
                if (!integerToken(lexeme)) {
                    yield new Numeric("UNSUPPORTED_NUMBER", "");
                }
                BigInteger integer = new BigInteger(lexeme);
                yield integer.abs().compareTo(BigInteger.valueOf(9007199254740991L)) <= 0
                    ? new Numeric("OK", integer.toString()) : new Numeric("UNSAFE_NUMBER", "");
            }
            case "candidate-integer-string" -> integerToken(lexeme)
                ? new Numeric("OK", new BigInteger(lexeme).toString())
                : new Numeric("INVALID_NUMBER_LEXEME", "");
            case "candidate-decimal-string" -> {
                if (!lexeme.matches("-?(0|[1-9][0-9]*)\\.[0-9]+")
                    || (lexeme.startsWith("-") && new BigDecimal(lexeme).signum() == 0)) {
                    yield new Numeric("INVALID_NUMBER_LEXEME", "");
                }
                yield new Numeric("OK", new BigDecimal(lexeme).toPlainString());
            }
            default -> new Numeric("UNKNOWN_NUMBER_KIND", "");
        };
    }

    /** Raw valueJson retains count spellings that ordinary JSON number decoding would erase. */
    private String unionDiagnostic(JsonNode vector) throws Exception {
        String tag = text(vector, "tag");
        if (!Set.of("count", "label").contains(tag)) {
            return "UNKNOWN_VARIANT";
        }
        String token = text(vector, "valueJson");
        boolean valid = tag.equals("count")
            ? integerToken(token)
                && new BigInteger(token).abs().compareTo(BigInteger.valueOf(9007199254740991L)) <= 0
            : mapper.readTree(token).isTextual();
        return valid ? "OK" : "INVALID_VARIANT_VALUE";
    }

    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Delegates HMAC to the real signer while independently checking its fixed preimage oracle. */
    private String signature(JsonNode request) {
        return TransitionWorkerSignature.sign(text(request, "secret"), text(request, "method"),
            signaturePath(request), text(request, "timestamp"), text(request, "nonce"),
            Base64.getDecoder().decode(text(request, "bodyBase64")));
    }

    private String signaturePath(JsonNode request) {
        // The signer maps a null path to the root. Keep null out of the Java call.
        return request.path("path").isNull() ? "/" : text(request, "path");
    }

    /** Mirrors the signer's Java String.isBlank rule, including its Unicode distinctions. */
    private String normalizedPath(String path) {
        if (path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String signaturePreimage(JsonNode request) throws Exception {
        return String.join("\n", text(request, "method"), normalizedPath(signaturePath(request)),
            text(request, "timestamp"), text(request, "nonce"),
            sha(Base64.getDecoder().decode(text(request, "bodyBase64"))));
    }

    /** Rejects unsupported candidate declarations before any test invocation can begin. */
    private String admission(JsonNode vector) {
        if (!exactNumber(vector.path("schemaVersion"), 1)) {
            return "UNKNOWN_SCHEMA";
        }
        if (!text(vector, "profile").equals("tpf-polyglot-probe/0")) {
            return "UNKNOWN_PROFILE";
        }
        if (!vector.path("capabilities").isArray() || vector.path("capabilities").size() != 1) {
            return "UNSUPPORTED_CAPABILITY";
        }
        if (!text(vector, "retryOwner").equals("local-invocation-shell")
            || !exactNumber(vector.path("retryLimit"), 2)) {
            return "POLICY_CONFLICT";
        }
        for (JsonNode capability : vector.path("capabilities")) {
            if (!capability.asText().equals("pure-unary")) {
                return "UNSUPPORTED_CAPABILITY";
            }
        }
        return text(vector, "payloadType").equals("probe.Value/1") ? "OK" : "UNKNOWN_PAYLOAD_TYPE";
    }

    private String contextDiagnostic(Map<String, String> context) {
        return Set.of("executionId", "traceId").containsAll(context.keySet())
            ? "OK" : "RESTRICTED_CONTEXT";
    }

    /** Validates portable failure data before any retry flag can authorize another A attempt. */
    private String failureDiagnostic(JsonNode failure) {
        if (!failure.isObject() || !failure.path("code").isTextual()
            || !failure.path("message").isTextual() || !failure.path("retryable").isBoolean()
            || !failure.path("owner").isTextual() || !text(failure, "stepId").equals("A")) {
            return "MALFORMED_FAILURE";
        }
        if (!Set.of("BUSINESS_REJECTION", "TRANSIENT").contains(text(failure, "code"))) {
            return "UNKNOWN_FAILURE";
        }
        return !text(failure, "owner").equals("local-invocation-shell")
            || failure.path("retryable").asBoolean() != text(failure, "code").equals("TRANSIENT")
            ? "FAILURE_POLICY_CONFLICT" : "OK";
    }

    /**
     * One local asynchronous invocation with private counters, context and cancellation state.
     * Delivery-history fixtures reuse this shell only as policy simulations; they open no sockets
     * and do not exercise durable coordinator delivery, retries or remote outcome recovery.
     */
    private final class Invocation {
        private final JsonNode policy;
        private final ExecutionContext context;
        private final Optional<StartBarrier> startBarrier;
        private final List<Observation> observations = new ArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private int aAttempts;
        private int bAttempts;
        private int callerAttempts;

        Invocation(JsonNode policy, ExecutionContext context) {
            this(policy, context, Optional.empty());
        }

        Invocation(JsonNode policy, ExecutionContext context, Optional<StartBarrier> startBarrier) {
            this.policy = policy;
            this.context = context;
            this.startBarrier = startBarrier;
        }

        /** Runs the probe's fixed A-then-B shape; only this shell decides whether A can retry. */
        CompletableFuture<Outcome> run() {
            return CompletableFuture.supplyAsync(() -> {
                String owner = text(policy, "retryOwner");
                boolean coordinator = owner.equals("coordinator-per-delivery");
                JsonNode count = policy.path("deliveries");
                int deliveries = count.asInt();
                if ((!coordinator && !Set.of("local-invocation-shell", "callee-invocation-shell").contains(owner))
                    || deliveries < 1 || (!coordinator && deliveries != 1) || !exactNumber(count, deliveries)) {
                    return outcome("POLICY_CONFLICT", 0);
                }
                for (int delivery = 0; delivery < deliveries; delivery++) {
                    callerAttempts++;
                    for (int attempt = 0; attempt <= policy.path("retryLimit").asInt(); attempt++) {
                        aAttempts++;
                        long value;
                        int attemptNumber = attempt;
                        try {
                            value = CompletableFuture.supplyAsync(() -> invokeA(attemptNumber)).join();
                        } catch (CompletionException exception) {
                            if (!(exception.getCause() instanceof InvocationFailure failed)) {
                                throw exception;
                            }
                            if (cancelled.get()) {
                                return outcome("CANCELLED", 0);
                            }
                            String diagnostic = failureDiagnostic(failed.envelope);
                            if (!diagnostic.equals("OK")) {
                                return outcome(diagnostic, 0);
                            }
                            if (!failed.envelope.path("retryable").asBoolean()) {
                                if (!policy.has("failureEnvelope")) {
                                    equal(failed.envelope, corpus.path("failureShape"),
                                        "executions/" + text(policy, "id") + "/failureShape");
                                }
                                return outcome(text(failed.envelope, "code"), 0);
                            }
                            continue;
                        }
                        if (policy.path("cancelBeforeB").asBoolean()) {
                            cancelled.set(true);
                        }
                        if (cancelled.get()) {
                            return outcome("CANCELLED", 0);
                        }
                        bAttempts++;
                        long result = CompletableFuture.supplyAsync(() -> invokeB(value)).join();
                        return outcome("COMPLETED", result);
                    }
                }
                return outcome("RETRY_EXHAUSTED", 0);
            });
        }

        /** Captures A's real context and throws fixture envelopes from inside the async stage. */
        private long invokeA(int attempt) {
            observations.add(new Observation("A", context.executionId(), context.traceId()));
            startBarrier.ifPresent(StartBarrier::arriveAndAwait);
            if (policy.path("cancelDuringA").asBoolean()) {
                cancelled.set(true);
            }
            if (policy.has("failureEnvelope")) {
                throw new InvocationFailure(policy.path("failureEnvelope"));
            }
            if (policy.path("terminal").asBoolean()) {
                throw new InvocationFailure(mapper.valueToTree(new Failure(
                    "BUSINESS_REJECTION", "input rejected", false, "local-invocation-shell", "A")));
            }
            if (attempt < policy.path("failuresBeforeSuccess").asInt()) {
                throw new InvocationFailure(mapper.valueToTree(new Failure(
                    "TRANSIENT", "temporary", true, "local-invocation-shell", "A")));
            }
            return policy.path("input").asLong() + 1L;
        }

        private long invokeB(long value) {
            observations.add(new Observation("B", context.executionId(), context.traceId()));
            return value * 2L;
        }

        private Outcome outcome(String code, long result) {
            return new Outcome(aAttempts, bAttempts, callerAttempts, code, result);
        }
    }

    /** Checks wrapper state and policy against input JSON, with a separate round-trip oracle. */
    private void checkPresence() throws Exception {
        for (JsonNode vector : corpus.path("presence")) {
            String id = "presence/" + text(vector, "id");
            Field field = mapper.readValue(text(vector, "json"), Field.class);
            String actualState = state(field.value());
            equal(actualState, text(vector, "state"), id + "/state");
            String diagnostic = actualState.equals("ABSENT") && !vector.path("optional").asBoolean()
                ? "MISSING_REQUIRED"
                : actualState.equals("NULL") && !vector.path("nullable").asBoolean()
                    ? "NULL_FORBIDDEN" : "OK";
            equal(diagnostic, text(vector, "diagnostic"), id + "/diagnostic");
            equal(mapper.readTree(mapper.writeValueAsString(field)), mapper.readTree(text(vector, "json")),
                id + "/roundtrip");
        }
        boolean rejected = false;
        try {
            mapper.writeValueAsString(CanonicalFieldValue.absent());
        } catch (com.fasterxml.jackson.databind.JsonMappingException exception) {
            rejected = true;
        }
        equal(rejected, true, "presence/rootAbsent");
    }

    /** Checks numeric lexical fidelity and the closed logical union without class-name loading. */
    private void checkValues() throws Exception {
        for (JsonNode vector : corpus.path("numbers")) {
            String id = "numbers/" + text(vector, "id");
            Numeric actual = number(vector);
            equal(actual.diagnostic(), text(vector, "diagnostic"), id + "/diagnostic");
            if (actual.diagnostic().equals("OK")) {
                equal(actual.exact(), text(vector, "exact"), id + "/exact");
            }
        }
        int index = 0;
        for (JsonNode vector : corpus.path("unions")) {
            equal(unionDiagnostic(vector), text(vector, "diagnostic"), "unions/" + index++);
        }
        for (JsonNode vector : corpus.path("bytes")) {
            equal(sha(Base64.getDecoder().decode(text(vector, "base64"))), text(vector, "sha256"),
                "bytes/" + text(vector, "id") + "/sha256");
        }
    }

    /** Checks byte identity, normalized signature paths and mutations using their actual bytes. */
    private void checkSignatures() throws Exception {
        JsonNode request = corpus.path("signature");
        equal(sha(Base64.getDecoder().decode(text(request, "bodyBase64"))), text(request, "bodySha256"),
            "signature/bodySha256");
        equal(signature(request), text(request, "hmac"), "signature/hmac");
        equal(signaturePreimage(request), text(request, "preimage"), "signature/preimage");
        equal(TransitionWorkerSignature.parseTimestamp(text(request, "timestamp")), 1789380930000L,
            "signature/timestamp");
        for (JsonNode vector : corpus.path("signaturePaths")) {
            ObjectNode candidate = request.deepCopy();
            candidate.set("path", vector.path("path"));
            String id = "signaturePaths/" + text(vector, "id");
            equal(signaturePreimage(candidate), text(vector, "preimage"), id + "/preimage");
            equal(signature(candidate), text(vector, "hmac"), id + "/hmac");
        }
        int index = 0;
        for (JsonNode mutation : corpus.path("authMutations")) {
            String field = text(mutation, "field");
            String diagnostic;
            if (corpus.path("pins").has(field)) {
                ObjectNode candidate = corpus.path("pins").deepCopy();
                candidate.put(field, text(mutation, "value"));
                diagnostic = candidate.equals(corpus.path("pins")) ? "OK" : "IDENTITY_MISMATCH";
            } else {
                ObjectNode candidate = request.deepCopy();
                candidate.put(field, text(mutation, "value"));
                diagnostic = TransitionWorkerSignature.matches(signature(candidate), text(request, "hmac"))
                    ? "OK" : "BAD_SIGNATURE";
            }
            equal(diagnostic, text(mutation, "diagnostic"), "authMutations/" + index++);
        }
        Set<String> nonces = new HashSet<>();
        equal(nonces.add(text(request, "nonce")), true, "signature/nonceFirst");
        equal(nonces.add(text(request, "nonce")), false, "signature/nonceReplay");
    }

    /** Invalid admission and failure data remain observable without invoking unsupported behavior. */
    private void checkAdmissionAndFailures() {
        for (JsonNode vector : corpus.path("admission")) {
            String id = "admission/" + text(vector, "id");
            String diagnostic = admission(vector);
            int invoked = diagnostic.equals("OK")
                ? new Invocation(corpus.path("executions").get(0),
                    new ExecutionContext("admitted", "trace")).run().join().aAttempts() : 0;
            equal(diagnostic, text(vector, "diagnostic"), id + "/diagnostic");
            equal(invoked, text(vector, "diagnostic").equals("OK") ? 1 : 0, id + "/invocations");
        }
        for (JsonNode vector : corpus.path("failures")) {
            equal(failureDiagnostic(vector.path("envelope")), text(vector, "diagnostic"),
                "failures/" + text(vector, "id"));
        }
        for (JsonNode key : corpus.path("forbiddenContextKeys")) {
            equal(contextDiagnostic(Map.of("executionId", "x", key.asText(), "secret")),
                "RESTRICTED_CONTEXT", "context/forbidden/" + key.asText());
        }
    }

    /** Reparse serialized results so JSON integer node widths do not obscure value equality. */
    private JsonNode outcomeTree(Outcome outcome) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(outcome));
    }

    /** Fixed outcome/count oracles cover retries, cancellation, failure admission and wide results. */
    private void checkExecutions() throws Exception {
        for (JsonNode vector : corpus.path("executions")) {
            Outcome actual = new Invocation(vector, new ExecutionContext(text(vector, "id"), "trace"))
                .run().join();
            equal(outcomeTree(actual), vector.path("expected"),
                "executions/" + text(vector, "id") + "/outcome");
        }
    }

    /** Both A stages reach the barrier before either can produce a result or proceed to B. */
    private void checkContextIsolation() {
        List<CompletableFuture<Outcome>> interleaved = new ArrayList<>();
        List<Invocation> invocations = new ArrayList<>();
        StartBarrier barrier = new StartBarrier(corpus.path("contexts").size());
        for (JsonNode context : corpus.path("contexts")) {
            equal(contextDiagnostic(Map.of("executionId", text(context, "executionId"),
                "traceId", text(context, "traceId"))), "OK", "context/allowed");
            ObjectNode policy = corpus.path("executions").get(0).deepCopy();
            policy.put("input", context.path("input").asLong());
            Invocation invocation = new Invocation(policy,
                new ExecutionContext(text(context, "executionId"), text(context, "traceId")),
                Optional.of(barrier));
            invocations.add(invocation);
            interleaved.add(invocation.run());
        }
        CompletableFuture.allOf(interleaved.toArray(CompletableFuture[]::new)).join();
        for (int index = 0; index < interleaved.size(); index++) {
            JsonNode context = corpus.path("contexts").get(index);
            String id = "contexts/" + text(context, "executionId");
            equal(interleaved.get(index).join().result(), context.path("expected").asLong(), id + "/result");
            equal(invocations.get(index).observations, List.of(
                new Observation("A", text(context, "expectedExecutionId"), text(context, "expectedTraceId")),
                new Observation("B", text(context, "expectedExecutionId"), text(context, "expectedTraceId"))),
                id + "/observations");
        }
    }

    /** Keeps fixed discrimination witnesses separate from the runner's corrupted-oracle cases. */
    private void checkDiscriminationWitnesses() {
        equal(state(CanonicalFieldValue.nullValue()).equals("ABSENT"), false, "witness/nullState");
        equal(number(corpus.path("numbers").get(2)).exact().equals("9007199254740992"), false,
            "witness/integerPrecision");
        equal(new Invocation(corpus.path("executions").get(1), new ExecutionContext("mutation", "trace"))
            .run().join().aAttempts() == 2, false, "witness/retryCount");
    }

    private void run() throws Exception {
        checkPresence();
        checkValues();
        checkSignatures();
        checkAdmissionAndFailures();
        checkExecutions();
        checkContextIsolation();
        checkDiscriminationWitnesses();
        System.out.println("{\"language\":\"java\",\"status\":\"PASS\",\"checks\":" + checks
            + ",\"profile\":\"tpf-polyglot-probe/0\",\"evidence\":\"handcrafted-non-production\"}");
    }

    /** With no argument, reads the shared corpus from the documented repository-root location. */
    public static void main(String[] args) throws Exception {
        Path path = args.length == 0 ? Path.of("conformance/polyglot-v0/vectors.json") : Path.of(args[0]);
        new PolyglotProbeConsumer(path).run();
    }
}
