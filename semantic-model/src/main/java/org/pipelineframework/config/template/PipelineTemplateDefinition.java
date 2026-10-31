package org.pipelineframework.config.template;

import java.util.List;

/**
 * A locally authored compile-time pipeline definition fragment.
 *
 * <p>The entry key in {@link PipelineTemplateConfig#pipelines()} supplies its logical identity.
 * This fragment intentionally has no deployment or source-location identity, leaving packaged
 * definition linking to enter through the same logical-reference seam later.</p>
 * The concrete outcomes selected by {@code recur} re-enter this definition through a
 * compiler-owned bounded continuation after its authored steps have completed.
 */
public record PipelineTemplateDefinition(
    String inputContract,
    String outputContract,
    List<PipelineTemplateStep> steps,
    List<String> recur
) {
    public PipelineTemplateDefinition {
        if (inputContract == null || inputContract.isBlank()) {
            throw new IllegalArgumentException("inputContract must not be blank");
        }
        if (outputContract == null || outputContract.isBlank()) {
            throw new IllegalArgumentException("outputContract must not be blank");
        }
        inputContract = inputContract.trim();
        outputContract = outputContract.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
        recur = List.copyOf(java.util.Objects.requireNonNull(recur, "recur must not be null"));
        if (recur.stream().anyMatch(type -> type.isBlank() || !type.equals(type.trim()))
            || new java.util.HashSet<>(recur).size() != recur.size()) {
            throw new IllegalArgumentException("recur must contain distinct non-blank canonical type names");
        }
    }

    public PipelineTemplateDefinition(String inputContract, String outputContract, List<PipelineTemplateStep> steps) {
        this(inputContract, outputContract, steps, List.of());
    }
}
