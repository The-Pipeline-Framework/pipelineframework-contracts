/*
 * Copyright (c) 2023-2025 Mariano Barcia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pipelineframework.config.pipeline;

import java.util.Optional;

/**
 * Deferred-completion modifier parsed from an ordinary operation in pipeline.yaml.
 *
 * @param correlation correlation configuration
 * @param transport transport adapter configuration
 * @param completion optional request-aware completion projection
 * @param callback optional callback initiation contract
 * @param accepts selected canonical operation-output variants
 * @param output completed canonical outcome for the selected branch
 */
public record PipelineYamlAwaitConfig(
    PipelineYamlAwaitCorrelation correlation,
    Optional<PipelineYamlAwaitTransport> transport,
    Optional<PipelineYamlAwaitCompletion> completion,
    Optional<PipelineYamlAwaitCallback> callback,
    java.util.List<String> accepts,
    Optional<String> output
) {
    public PipelineYamlAwaitConfig(PipelineYamlAwaitCorrelation correlation,
        Optional<PipelineYamlAwaitTransport> transport, Optional<PipelineYamlAwaitCompletion> completion,
        Optional<PipelineYamlAwaitCallback> callback) {
        this(correlation, transport, completion, callback, java.util.List.of(), Optional.empty());
    }

    public PipelineYamlAwaitConfig(PipelineYamlAwaitCorrelation correlation, PipelineYamlAwaitTransport transport,
        Optional<PipelineYamlAwaitCompletion> completion) {
        this(correlation, Optional.of(transport), completion, Optional.empty());
    }
    public PipelineYamlAwaitConfig(
        PipelineYamlAwaitCorrelation correlation,
        PipelineYamlAwaitTransport transport
    ) {
        this(correlation, transport, Optional.empty());
    }

    public PipelineYamlAwaitConfig {
        output = java.util.Objects.requireNonNull(output, "output").map(String::trim);
        accepts = java.util.List.copyOf(java.util.Objects.requireNonNull(accepts, "accepts"));
        java.util.Objects.requireNonNull(callback, "callback");
        if (output.filter(String::isBlank).isPresent() || accepts.isEmpty() != output.isEmpty()) {
            throw new IllegalArgumentException("await.accepts and await.output must be declared together");
        }
        if (accepts.stream().anyMatch(value -> value.isBlank())) {
            throw new IllegalArgumentException("await.accepts must contain non-blank canonical type references");
        }
        if (!accepts.isEmpty() && callback.isPresent()) {
            throw new IllegalArgumentException("await.accepts supports post-operation transport completion only");
        }
        correlation = correlation == null ? new PipelineYamlAwaitCorrelation("interactionId") : correlation;
        java.util.Objects.requireNonNull(transport, "transport");
        if (transport.isPresent() == callback.isPresent()) {
            throw new IllegalArgumentException("await requires exactly one of transport or callback");
        }
        completion = completion == null ? Optional.empty() : completion;
    }
}
