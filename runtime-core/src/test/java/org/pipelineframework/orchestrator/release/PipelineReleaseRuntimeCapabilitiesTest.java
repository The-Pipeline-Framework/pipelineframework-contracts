package org.pipelineframework.orchestrator.release;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PipelineReleaseRuntimeCapabilitiesTest {
    @TempDir Path root;
    private static final String FACT = "schemaVersion=1\ncommandEffectStore.provider=dynamo\ncommandEffectStore.selectedBeans=example.DurableStore\ncommandEffectStore.recoverySupported=true\n";
    @Test void requiresActualRecoverableSelectionAndRejectsAmbiguousOrMalformedFacts() throws Exception {
        assertEquals("dynamo", PipelineReleaseRuntimeCapabilities.parse(FACT.getBytes()).requireRecoverable(PipelineReleaseRuntimeCapabilities.COMMAND_EFFECT_DYNAMODB).provider());
        for(String value:List.of(FACT.replace("true", "false"), FACT.replace("example.DurableStore", ""),
            FACT.replace("example.DurableStore", "example.DurableStore,example.OtherStore"),
            FACT + "schemaVersion=1\n", FACT.replace("schemaVersion=1", "schemaVersion=2"),
            FACT.replace("provider=dynamo", "provider=memory"), FACT.replace("=true", "=yes"), FACT+"unknown=fact\n")) {
            assertThrows(Exception.class, () -> PipelineReleaseRuntimeCapabilities.parse(value.getBytes()).requireRecoverable(PipelineReleaseRuntimeCapabilities.COMMAND_EFFECT_DYNAMODB));
        }
        assertThrows(IllegalArgumentException.class, () -> PipelineReleaseRuntimeCapabilities.parse(new byte[PipelineReleaseRuntimeCapabilities.MAX_BYTES+1]));
    }
    @Test void packageAssociationUsesExactSelectedRootOrNestedJarAndPreservesUnrequestedCompatibility() throws Exception {
        for(String path:List.of(".", "lib/application.jar")) {
            verify(path, FACT, true, null);
            assertThrows(IllegalArgumentException.class, () -> verify(path, null, true, null));
            assertThrows(IllegalArgumentException.class, () -> verify(path, FACT.replace("true", "false"), true, null));
            verify(path, null, false, null);
        }
    }

    @Test void verifiesLambdaRootBuildFactsWithNestedCompiledTruthAndRejectsDuplicateFacts() throws Exception {
        String path = "lib/application.jar";
        verify(path, null, true, FACT);
        assertThrows(IllegalArgumentException.class, () -> verify(path, null, true, FACT.replace("true", "false")));
        var failure = assertThrows(IllegalArgumentException.class, () -> verify(path, FACT, true, FACT));
        assertTrue(failure.getMessage().contains("multiple locations"));
    }

    @Test void verifiesQueryOnlyAndCombinedStoresWhilePreservingLegacyCommandBytes() throws Exception {
        String query = PipelineReleaseRuntimeCapabilities.QUERY_CAPTURE_DYNAMODB;
        String command = PipelineReleaseRuntimeCapabilities.COMMAND_EFFECT_DYNAMODB;
        var selection = new PipelineReleaseRuntimeCapabilities.Selection("dynamo", List.of("example.DurableQueryStore"), true);
        var queryOnly = new PipelineReleaseRuntimeCapabilities(1, Map.of(query, selection));
        assertEquals(queryOnly, PipelineReleaseRuntimeCapabilities.parse(queryOnly.encode()));
        var legacy = PipelineReleaseRuntimeCapabilities.parse(FACT.getBytes());
        assertArrayEquals(FACT.getBytes(), legacy.encode());
        var both = new PipelineReleaseRuntimeCapabilities(1, Map.of(query, selection, command, legacy.requireRecoverable(command)));
        assertEquals(both, PipelineReleaseRuntimeCapabilities.parse(both.encode()));
        String path = "lib/application.jar";
        verify(path, null, List.of(query, command), new String(both.encode()));
        verify(path, null, List.of(query), new String(queryOnly.encode()));
        assertThrows(IllegalArgumentException.class, () -> verify(path, null, List.of(query), FACT));
        assertThrows(IllegalArgumentException.class, () -> verify(path, null, List.of(query, command), FACT));
        assertThrows(IllegalArgumentException.class, () -> verify(path, null, List.of(query), new String(both.encode()).replace("queryCaptureStore.provider=dynamo", "queryCaptureStore.provider=memory")));
    }

    private void verify(String path, String facts, boolean required, String rootFacts) throws Exception {
        verify(path, facts, required ? List.of(PipelineReleaseRuntimeCapabilities.COMMAND_EFFECT_DYNAMODB) : List.of(), rootFacts);
    }

    private void verify(String path, String facts, List<String> required, String rootFacts) throws Exception {
        Path metadata=Files.createTempDirectory(root,"truth");Files.writeString(metadata.resolve("pipeline-contract.json"),"{}");
        Map<String,byte[]> content=new LinkedHashMap<>();content.put(PipelineContractDescriptor.RESOURCE_PATH,"{}".getBytes());content.put("example/Entry.class",new byte[]{1});
        if(facts!=null)content.put(PipelineReleaseRuntimeCapabilities.RESOURCE_PATH,facts.getBytes());
        Path file=Files.createTempFile(root,"app",".zip");
        if(path.equals("."))Files.write(file,archive(content));
        else {
            Map<String,byte[]> outer=new LinkedHashMap<>();outer.put(path,archive(content));
            if(rootFacts!=null)outer.put(PipelineReleaseRuntimeCapabilities.RESOURCE_PATH,rootFacts.getBytes());
            Files.write(file,archive(outer));
        }
        var bindings = required.stream().map(capability -> new PipelineReleaseDeployment.Binding(capability, capability)).toList();
        var payload=Map.of("payloadTypeId","java.lang.String","payloadEncoding","json","payload","\"x\"");
        var deployment=new PipelineReleaseDeployment("test",List.of(new PipelineReleaseDeployment.Entry("entry","app","runtime",path,"example.Entry","TEST",required)),bindings,new PipelineReleaseDeployment.Check("entry",payload,payload,1000));
        var artifact=new PipelineReleaseArtifactDescriptor("app","lambda-zip",file.toUri().toString(),"sha256:"+"a".repeat(64),List.of("step"),List.of("local"));
        var release=new PipelineReleaseDescriptor(2,"pipeline","contract","A","app",List.of(artifact),deployment);
        new PipelineReleasePackageVerifier().verify(release,Map.of("app",file),metadata);
    }
    private static byte[] archive(Map<String,byte[]> entries)throws IOException {
        var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){for(var entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return output.toByteArray();
    }
}
