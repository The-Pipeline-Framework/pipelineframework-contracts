import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Test-only Java policy model. It does not call or replace PipelineRunnerCore.
 * Histories specify accepted event order; they are not a concurrency or replay test.
 */
public final class PolicyConsumer {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String SANITISED = "Unclassified business function failure";
    private static final Set<String> REQUIRED = Set.of(("success b-budget-zero b-budget-one exhaust-preserves-failure business-rejection raw-host-exception wire-unclassified unknown-code malformed-failure missing-failure-field lying-eligibility spoofed-owner spoofed-step unclassified-unsanitised raw-null implicit-return empty missing-value nonnull-null nullable-tagged-null legacy-retry legacy-recovery cancel-prestart cancel-inflight-success cancel-inflight-failure cancel-handoff cancel-pending-retry cancel-after-b-start cancel-duplicate complete-before-cancel failure-before-cancel invalid-before-cancel context-missing context-absent context-blank context-type context-authority context-non-ascii unsupported-owner invalid-budget invalid-declaration budget-equivalent budget-rounded budget-negative budget-too-large budget-string").split(" "));

    private static void check(String id, Object actual, Object expected) {
        if (!actual.equals(expected)) {
            System.err.println("CONFORMANCE_MISMATCH:" + id);
            System.err.println("Expected " + expected + ", got " + actual);
            System.exit(1);
        }
    }
    private static Set<String> keys(JsonNode value) {
        var names = new HashSet<String>();
        if (value.isObject()) value.fieldNames().forEachRemaining(names::add);
        return names;
    }
    private static boolean shape(JsonNode value, String... names) {
        return value.isObject() && keys(value).equals(Set.of(names));
    }
    private static final class Rejection extends RuntimeException {
        Rejection(String code) { super(code); }
    }
    private static Map<String,String> bindContext(JsonNode value) {
        if (!shape(value, "executionId", "traceId")) throw new Rejection("INVALID_CONTEXT");
        var copy = new HashMap<String,String>();
        for (String key : List.of("executionId", "traceId")) {
            var field = value.path(key);
            if (!field.isTextual() || field.textValue().matches("[ \\t\\r\\n]*")) throw new Rejection("INVALID_CONTEXT");
            copy.put(key, field.textValue());
        }
        return Map.copyOf(copy); // no caller or handler can mutate the admitted snapshot
    }
    private static ObjectNode failure(JsonNode value, String step) {
        if (!shape(value,"code","message","owner","stepId","retryable")
            || List.of("code","message","owner","stepId").stream().anyMatch(k -> !value.path(k).isTextual())
            || !value.path("retryable").isBoolean()) throw new Rejection("MALFORMED_FAILURE");
        String code = value.path("code").asText();
        if (!Set.of("TRANSIENT","BUSINESS_REJECTION","UNCLASSIFIED").contains(code)) throw new Rejection("UNKNOWN_FAILURE_CODE");
        if (!value.path("owner").asText().equals("LOCAL_SHELL") || !value.path("stepId").asText().equals(step)
            || value.path("retryable").booleanValue() != code.equals("TRANSIENT")
            || (code.equals("UNCLASSIFIED") && !value.path("message").asText().equals(SANITISED))) throw new Rejection("FAILURE_BINDING_MISMATCH");
        return value.deepCopy();
    }
    private static ObjectNode result(JsonNode value, boolean nullable) {
        if (shape(value,"kind") && value.path("kind").asText().equals("EMPTY")) throw new Rejection("UNSUPPORTED_EMPTY");
        if (!shape(value,"kind","value") || !value.path("kind").asText().equals("VALUE")
            || (value.path("value").isNull() && !nullable)) throw new Rejection("INVALID_VALUE");
        return value.deepCopy();
    }
    private static boolean configNumber(JsonNode value, int expected) {
        return value.isNumber() && value.decimalValue().compareTo(BigDecimal.valueOf(expected)) == 0;
    }
    private static ObjectNode kind(String name) { return JSON.createObjectNode().put("kind",name); }

    /** One pending attempt and one committed terminal outcome, with explicit dispatch. */
    private static final class History {
        private final ObjectNode counts = JSON.createObjectNode().put("A",0).put("B",0).put("C",0);
        private final ObjectNode inputs = JSON.createObjectNode();
        private final ArrayNode delivery = JSON.createArrayNode();
        private Optional<ObjectNode> terminal = Optional.empty();
        private Optional<String> active = Optional.empty();
        private Map<String,String> context = Map.of();
        private String next = "A";
        private JsonNode input = JSON.getNodeFactory().numberNode(3);
        private int observations;

        History() { for (String step : List.of("A","B","C")) inputs.putArray(step); }
        private void finish(ObjectNode outcome) {
            terminal = Optional.of(outcome);
            observations++;
            if (!outcome.path("kind").asText().equals("CANCELLED")) delivery.add(outcome);
        }
        private void reject(Rejection failure) { finish(kind("REJECTED").put("diagnostic",failure.getMessage())); }
        ObjectNode run(JsonNode row) {
            try {
                context = bindContext(row.path("context"));
                if (List.of("nullable","legacyRetry","recovery").stream().anyMatch(k -> !row.path(k).isBoolean())) throw new Rejection("INVALID_POLICY");
                if (!row.path("owner").asText().equals("LOCAL_SHELL") || row.path("legacyRetry").asBoolean() || row.path("recovery").asBoolean()) throw new Rejection("POLICY_OWNERSHIP_CONFLICT");
                var limits = row.path("retryLimits");
                if (!shape(limits,"A","B","C") || List.of("A","B","C").stream().anyMatch(k -> !configNumber(limits.path(k),0) && !configNumber(limits.path(k),1) && !configNumber(limits.path(k),2))) throw new Rejection("INVALID_POLICY");
            } catch (Rejection error) { reject(error); }
            for (JsonNode event : row.path("events")) {
                if (terminal.isPresent()) continue; // ignore late data, cancellation and dispatch
                String action = event.path("kind").asText();
                if (action.equals("cancel")) { finish(kind("CANCELLED")); continue; }
                String step = event.path("step").asText();
                if (action.equals("dispatch")) {
                    if (active.isPresent() || !next.equals(step)) throw new IllegalStateException("Invalid test dispatch");
                    active = Optional.of(step);
                    counts.put(step,counts.path(step).intValue()+1);
                    ((ArrayNode)inputs.path(step)).add(input.deepCopy());
                    if (!context.equals(bindContext(row.path("context")))) throw new IllegalStateException("Context changed across attempts");
                    continue;
                }
                if (!active.equals(Optional.of(step))) throw new IllegalStateException("Invalid test completion");
                active = Optional.empty();
                try {
                    if (action.equals("failure") || action.equals("throw")) {
                        JsonNode candidate = event.path("failure");
                        if (action.equals("throw")) {
                            try { throw new IllegalStateException("private exception detail must not escape"); }
                            catch (IllegalStateException ignored) {
                                candidate = JSON.createObjectNode().put("code","UNCLASSIFIED").put("message",SANITISED).put("owner","LOCAL_SHELL").put("stepId",step).put("retryable",false);
                            }
                        }
                        var mapped = failure(candidate,step);
                        if (mapped.path("code").asText().equals("TRANSIENT") && counts.path(step).intValue() <= row.path("retryLimits").path(step).intValue()) next=step;
                        else finish(kind("FAILED").set("failure",mapped));
                    } else if (action.equals("return")) {
                        // Untagged JSON null models raw Java null: it is never a VALUE carrier.
                        var mapped = result(event.path("outcome"),row.path("nullable").asBoolean());
                        if (step.equals("C")) finish(kind("COMPLETED").set("outcome",mapped));
                        else { next = Character.toString(step.charAt(0)+1); input = mapped.path("value"); }
                    } else throw new IllegalStateException("Unknown test event");
                } catch (Rejection error) { reject(error); }
            }
            var output = JSON.createObjectNode();
            output.set("attempts",counts); output.set("attemptInputs",inputs);
            output.set("terminal",terminal.orElseThrow(() -> new IllegalStateException("Incomplete test history")));
            output.put("observations",observations); output.set("delivery",delivery);
            return output;
        }
    }
    private static JsonNode isolation(JsonNode inputs) {
        var callers = new ArrayList<ObjectNode>();
        var bound = new ArrayList<Map<String,String>>();
        for (JsonNode input : inputs) { callers.add(input.deepCopy()); bound.add(bindContext(callers.getLast())); }
        callers.forEach(v -> v.put("executionId","caller-mutated"));
        var seen = JSON.createArrayNode();
        for (var context : bound.reversed()) {
            for (int attempt=0; attempt<2; attempt++) {
                seen.add(JSON.valueToTree(context));
                try { context.put("traceId","handler-mutated"); }
                catch (UnsupportedOperationException expected) { /* view remains immutable */ }
            }
        }
        return seen;
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length==0 ? "conformance/polyglot-policy-v1/vectors.json" : args[0]);
        JsonNode corpus = JSON.readTree(path.toFile());
        check("corpus/profile",corpus.path("profile").asText(),"tpf-polyglot-unary-policy/1");
        check("corpus/schema",configNumber(corpus.path("schemaVersion"),1),true);
        var ids = new ArrayList<String>();
        corpus.path("cases").forEach(row -> ids.add(row.path("id").asText()));
        check("corpus/cases",ids.size()==new HashSet<>(ids).size() && new HashSet<>(ids).equals(REQUIRED),true);
        for (JsonNode row : corpus.path("cases")) check("cases/"+row.path("id").asText(),new History().run(row),row.path("expected"));
        check("context/isolation",isolation(corpus.path("isolation").path("inputs")),corpus.path("isolation").path("expected"));
        System.out.println(JSON.createObjectNode().put("language","java").put("cases",ids.size()).put("status","PASS"));
    }
}
