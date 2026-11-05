import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.pipelineframework.config.pipeline.PipelineJson;
import org.pipelineframework.orchestrator.TransitionWorkerSignature;
import org.pipelineframework.type.CanonicalFieldValue;

/** Test-only independent consumer of candidate fixtures, never a production runtime. */
public final class PolyglotProbeConsumer {
    private final ObjectMapper mapper = PipelineJson.mapper();
    private final JsonNode corpus;
    private int checks;
    record Field(CanonicalFieldValue<JsonNode> value) {}
    record ExecutionContext(String executionId, String traceId) {}
    record Failure(String code, String message, boolean retryable, String owner, String stepId) {}
    record Observation(String step, String executionId, String traceId) {}
    static final class InvocationFailure extends RuntimeException {
        final Failure failure; InvocationFailure(Failure failure) { super(failure.message()); this.failure=failure; }
    }
    record Outcome(int aAttempts, int bAttempts, int callerAttempts, String outcome, int result) {}
    record Numeric(String diagnostic, String exact) {}
    private PolyglotProbeConsumer(Path path) throws Exception { corpus = mapper.readTree(Files.readString(path)); equal(corpus.path("schemaVersion").asInt(),1); equal(corpus.path("profile").asText(),"tpf-polyglot-probe/0"); }
    private void equal(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) throw new AssertionError(actual + " != " + expected);
        checks++;
    }
    private String text(JsonNode v, String field) { return v.path(field).asText(); }
    private String state(CanonicalFieldValue<?> field) { return field.isAbsent() ? "ABSENT" : field.isNull() ? "NULL" : "VALUE"; }
    private Numeric number(JsonNode v) {
        String kind = text(v,"kind"), lexeme = text(v,"lexeme");
        return switch (kind) {
            case "json-safe-integer" -> {
                if (!lexeme.matches("-?(0|[1-9]\\d*)") || lexeme.equals("-0")) yield new Numeric("UNSUPPORTED_NUMBER", "");
                BigInteger n = new BigInteger(lexeme);
                yield n.abs().compareTo(BigInteger.valueOf(9007199254740991L)) <= 0
                    ? new Numeric("OK", n.toString()) : new Numeric("UNSAFE_NUMBER", "");
            }
            case "candidate-integer-string" -> lexeme.matches("-?(0|[1-9]\\d*)") && !lexeme.equals("-0") ? new Numeric("OK",new BigInteger(lexeme).toString()) : new Numeric("INVALID_NUMBER_LEXEME","");
            case "candidate-decimal-string" -> {
                if (!lexeme.matches("-?(0|[1-9]\\d*)\\.\\d+") || (lexeme.startsWith("-") && new BigDecimal(lexeme).signum()==0)) yield new Numeric("INVALID_NUMBER_LEXEME","");
                yield new Numeric("OK",new BigDecimal(lexeme).toPlainString());
            }
            default -> new Numeric("UNKNOWN_NUMBER_KIND", "");
        };
    }
    private String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private String signature(JsonNode s) {
        return TransitionWorkerSignature.sign(text(s,"secret"),text(s,"method"),text(s,"path"),text(s,"timestamp"),text(s,"nonce"),
            Base64.getDecoder().decode(text(s,"bodyBase64")));
    }
    private String admission(JsonNode v) {
        if (v.path("schemaVersion").asInt()!=1) return "UNKNOWN_SCHEMA";
        if (!text(v,"profile").equals("tpf-polyglot-probe/0")) return "UNKNOWN_PROFILE";
        if (!v.path("capabilities").isArray() || v.path("capabilities").size()!=1) return "UNSUPPORTED_CAPABILITY";
        if (!text(v,"retryOwner").equals("local-invocation-shell") || !v.path("retryLimit").isInt() || v.path("retryLimit").asInt()!=2) return "POLICY_CONFLICT";
        for (JsonNode c : v.path("capabilities")) if (!c.asText().equals("pure-unary")) return "UNSUPPORTED_CAPABILITY";
        return text(v,"payloadType").equals("probe.Value/1") ? "OK" : "UNKNOWN_PAYLOAD_TYPE";
    }
    private String contextDiagnostic(Map<String,String> context) {
        return Set.of("executionId","traceId").containsAll(context.keySet()) ? "OK" : "RESTRICTED_CONTEXT";
    }
    private final class Invocation {
        private final JsonNode policy;
        private final ExecutionContext context;
        private final List<Observation> observations = new ArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private int aAttempts, bAttempts, callerAttempts;
        Invocation(JsonNode policy, ExecutionContext context) {
            this.policy=policy; this.context=context;
        }
        CompletableFuture<Outcome> run() {
            // Independent invocation instances carry state; concurrent runs cannot share counters/context.
            return CompletableFuture.supplyAsync(() -> {
                for (int delivery=0; delivery<policy.path("deliveries").asInt(); delivery++) {
                    callerAttempts++;
                    for (int attempt=0; attempt<=policy.path("retryLimit").asInt(); attempt++) {
                        aAttempts++;
                        int value;
                        final int attemptNumber=attempt;
                        try {
                            value=CompletableFuture.supplyAsync(() -> {
                                observations.add(new Observation("A",context.executionId(),context.traceId()));
                                if (policy.path("cancelDuringA").asBoolean()) cancelled.set(true);
                                if (policy.path("terminal").asBoolean()) throw new InvocationFailure(
                                    new Failure("BUSINESS_REJECTION","input rejected",false,"local-invocation-shell","A"));
                                if (attemptNumber < policy.path("failuresBeforeSuccess").asInt()) throw new InvocationFailure(
                                    new Failure("TRANSIENT","temporary",true,"local-invocation-shell","A"));
                                return policy.path("input").asInt()+1;
                            }).join();
                        } catch (java.util.concurrent.CompletionException e) {
                            if (!(e.getCause() instanceof InvocationFailure failed)) throw e;
                            if (!failed.failure.retryable()) {
                                synchronized (PolyglotProbeConsumer.this) { equal(mapper.valueToTree(failed.failure),corpus.path("failureShape")); }
                                return outcome(failed.failure.code(),0);
                            }
                            continue;
                        }
                        if (policy.path("cancelBeforeB").asBoolean()) cancelled.set(true);
                        if (cancelled.get()) return outcome("CANCELLED",0);
                        bAttempts++;
                        int result = CompletableFuture.supplyAsync(() -> { observations.add(new Observation("B",context.executionId(),context.traceId())); return value*2; }).join();
                        return outcome("COMPLETED",result);
                    }
                }
                return outcome("RETRY_EXHAUSTED",0);
            });
        }
        private Outcome outcome(String code,int result) { return new Outcome(aAttempts,bAttempts,callerAttempts,code,result); }
    }
    private String failureDiagnostic(JsonNode f) {
        if (!f.has("code") || !f.path("message").isTextual() || !f.path("retryable").isBoolean() || !f.has("owner") || !text(f,"stepId").equals("A")) return "MALFORMED_FAILURE";
        if (!Set.of("BUSINESS_REJECTION","TRANSIENT").contains(text(f,"code"))) return "UNKNOWN_FAILURE";
        return !text(f,"owner").equals("local-invocation-shell") || f.path("retryable").asBoolean()!=text(f,"code").equals("TRANSIENT") ? "FAILURE_POLICY_CONFLICT" : "OK";
    }
    private void run() throws Exception {
        for (JsonNode v : corpus.path("presence")) {
            Field field=mapper.readValue(text(v,"json"),Field.class);
            String state=state(field.value()); equal(state,text(v,"state"));
            String diagnostic=state.equals("ABSENT")&&!v.path("optional").asBoolean()?"MISSING_REQUIRED":state.equals("NULL")&&!v.path("nullable").asBoolean()?"NULL_FORBIDDEN":"OK";
            equal(diagnostic,text(v,"diagnostic"));
            equal(mapper.readTree(mapper.writeValueAsString(field)),mapper.readTree(text(v,"json")));
        }
        boolean rejected=false;
        try { mapper.writeValueAsString(CanonicalFieldValue.absent()); } catch (com.fasterxml.jackson.databind.JsonMappingException e) { rejected=true; }
        equal(rejected,true);
        for (JsonNode v:corpus.path("numbers")) { Numeric n=number(v); equal(n.diagnostic(),text(v,"diagnostic")); if(n.diagnostic().equals("OK")) equal(n.exact(),text(v,"exact")); }
        for (JsonNode v:corpus.path("unions")) {
            String tag=text(v,"tag");
            String diagnostic=!Set.of("count","label").contains(tag)?"UNKNOWN_VARIANT":(tag.equals("count")?v.path("value").isIntegralNumber() && v.path("value").bigIntegerValue().abs().compareTo(BigInteger.valueOf(9007199254740991L))<=0:v.path("value").isTextual())?"OK":"INVALID_VARIANT_VALUE";
            equal(diagnostic,text(v,"diagnostic"));
        }
        for (JsonNode v:corpus.path("bytes")) equal(sha(Base64.getDecoder().decode(text(v,"base64"))),text(v,"sha256"));
        JsonNode sig=corpus.path("signature");
        equal(signature(sig),text(sig,"hmac"));
        equal(String.join("\n",text(sig,"method"),text(sig,"path"),text(sig,"timestamp"),text(sig,"nonce"),sha(Base64.getDecoder().decode(text(sig,"bodyBase64")))),text(sig,"preimage"));
        equal(TransitionWorkerSignature.parseTimestamp(text(sig,"timestamp")),1789380930000L);
        for(JsonNode mutation:corpus.path("authMutations")) {
            String field=text(mutation,"field"); String diagnostic;
            if(corpus.path("pins").has(field)) {
                var candidate=corpus.path("pins").deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)candidate).put(field,text(mutation,"value"));
                diagnostic=candidate.equals(corpus.path("pins"))?"OK":"IDENTITY_MISMATCH";
            } else {
                var candidate=(com.fasterxml.jackson.databind.node.ObjectNode)sig.deepCopy(); candidate.put(field,text(mutation,"value"));
                if(field.equals("bodyBase64")) candidate.put(field,Base64.getEncoder().encodeToString("tampered".getBytes(StandardCharsets.UTF_8)));
                diagnostic=TransitionWorkerSignature.matches(signature(candidate),text(sig,"hmac"))?"OK":"BAD_SIGNATURE";
            }
            equal(diagnostic,text(mutation,"diagnostic"));
        }
        Set<String> nonces=new HashSet<>(); equal(nonces.add(text(sig,"nonce")),true); equal(nonces.add(text(sig,"nonce")),false);
        for(JsonNode v:corpus.path("admission")) {
            String diagnostic=admission(v);
            int invoked=diagnostic.equals("OK")?new Invocation(corpus.path("executions").get(0),new ExecutionContext("admitted","trace")).run().join().aAttempts():0;
            equal(diagnostic,text(v,"diagnostic")); equal(invoked,text(v,"diagnostic").equals("OK")?1:0);
        }
        for(JsonNode v:corpus.path("failures")) equal(failureDiagnostic(v.path("envelope")),text(v,"diagnostic"));
        for(JsonNode key:corpus.path("forbiddenContextKeys")) equal(contextDiagnostic(Map.of("executionId","x",key.asText(),"secret")),"RESTRICTED_CONTEXT");
        for(JsonNode v:corpus.path("executions")) equal(mapper.valueToTree(new Invocation(v,new ExecutionContext(text(v,"id"),"trace")).run().join()),v.path("expected"));
        List<CompletableFuture<Outcome>> interleaved=new ArrayList<>();
        List<Invocation> invocations=new ArrayList<>();
        for(JsonNode c:corpus.path("contexts")) {
            equal(contextDiagnostic(Map.of("executionId",text(c,"executionId"),"traceId",text(c,"traceId"))),"OK");
            var policy=(com.fasterxml.jackson.databind.node.ObjectNode)corpus.path("executions").get(0).deepCopy();policy.put("input",c.path("input").asInt());
            Invocation invocation=new Invocation(policy,new ExecutionContext(text(c,"executionId"),text(c,"traceId")));
            invocations.add(invocation); interleaved.add(invocation.run());
        }
        CompletableFuture.allOf(interleaved.toArray(CompletableFuture[]::new)).join();
        for(int i=0;i<interleaved.size();i++) {
            JsonNode c=corpus.path("contexts").get(i); equal(interleaved.get(i).join().result(),c.path("expected").asInt());
            equal(invocations.get(i).observations,List.of(new Observation("A",text(c,"expectedExecutionId"),text(c,"expectedTraceId")),new Observation("B",text(c,"expectedExecutionId"),text(c,"expectedTraceId"))));
        }
        equal(state(CanonicalFieldValue.nullValue()).equals("ABSENT"),false);
        equal(number(corpus.path("numbers").get(2)).exact().equals("9007199254740992"),false);
        equal(new Invocation(corpus.path("executions").get(1),new ExecutionContext("mutation","trace")).run().join().aAttempts()==2,false);
        System.out.println("{\"language\":\"java\",\"status\":\"PASS\",\"checks\":"+checks+",\"profile\":\"tpf-polyglot-probe/0\",\"evidence\":\"handcrafted-non-production\"}");
    }
    public static void main(String[] args) throws Exception { new PolyglotProbeConsumer(Path.of(args[0])).run(); }
}
