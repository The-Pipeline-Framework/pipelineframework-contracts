package org.pipelineframework.config.template;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.pipelineframework.config.template.PipelineTemplateTypeDefinition.*;

class ConcreteTypeNamesTest {
    @Test
    void expandsAliasesAndNestedUnionsOnceInCanonicalOrder() {
        var model = new PipelineTemplateTypeModel(Map.of(
            "A", new RecordType("A", List.of()), "B", new RecordType("B", List.of()),
            "AliasA", new AliasType("AliasA", new PipelineTemplateTypeReference.Named("A")),
            "Inner", new UnionType("Inner", Map.of("b", variant("b", "B"), "a", variant("a", "AliasA"))),
            "Outer", new UnionType("Outer", Map.of("inner", variant("inner", "Inner"), "a", variant("a", "A"))),
            "Alias", new AliasType("Alias", new PipelineTemplateTypeReference.Named("Outer"))));
        assertEquals(List.of("A", "B"), List.copyOf(model.concreteTypeNames("Alias")));
        assertThrows(UnsupportedOperationException.class, () -> model.concreteTypeNames("A").clear());
        assertThrows(IllegalArgumentException.class, () -> model.concreteTypeNames("Missing"));
    }

    private Variant variant(String discriminator, String type) {
        return new Variant(discriminator, new PipelineTemplateTypeReference.Named(type));
    }
}
