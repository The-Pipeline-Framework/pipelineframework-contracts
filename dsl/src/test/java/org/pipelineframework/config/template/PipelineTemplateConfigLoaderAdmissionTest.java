/*
 * Copyright (c) 2023-2026 Mariano Barcia
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

package org.pipelineframework.config.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineTemplateConfigLoaderAdmissionTest {
    @TempDir
    Path tempDir;

    @Test
    void admitsNarrowerInputUnionAndRoutesLaterInputBranchesAtRoot() throws Exception {
        PipelineTemplateConfig config = load(lifecycle(false));

        assertEquals("DeploymentOperation", config.inputContract());
        assertEquals("CommandReceipt", config.outputContract());
        assertEquals(List.of("CreateApplicationCommand"), config.steps().getFirst().accepts());
        assertEquals(List.of("CreateEnvironmentCommand"), config.steps().get(2).accepts());
        assertFalse(config.typeModel().isAssignable("DeploymentOperation", "DeploymentState"));
    }

    @Test
    void admitsNarrowerInputUnionAndRoutesLaterInputBranchesInNamedDefinition() throws Exception {
        PipelineTemplateConfig config = load(lifecycle(true));
        var definition = config.pipelines().get("deployment-lifecycle");

        assertEquals("DeploymentOperation", definition.inputContract());
        assertEquals("CommandReceipt", definition.outputContract());
        assertEquals(5, definition.steps().size());
        assertEquals(List.of("CreateEnvironmentCommand"), definition.steps().get(2).accepts());
    }

    @Test
    void routesLaterAdmittedLeafUsingItsImplicitConcreteInputSelector() throws Exception {
        String yaml = lifecycle(true).replace(
            "input: DeploymentState, accepts: [CreateEnvironmentCommand]", "input: CreateEnvironmentCommand");
        PipelineTemplateConfig config = load(yaml);
        PipelineTemplateStep environment = config.pipelines().get("deployment-lifecycle").steps().get(2);

        assertEquals("CreateEnvironmentCommand", environment.inputTypeName());
        assertTrue(environment.accepts().isEmpty());
    }

    @Test
    void rejectsImplicitConcreteInputWithoutAnAdmittedOrProducedLeaf() {
        String yaml = lifecycle(true).replace("      - { name: Return Receipt",
            "      - { name: Foreign Branch, cardinality: ONE_TO_ONE, input: ForeignCommand, output: CommandReceipt }\n"
                + "      - { name: Return Receipt");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> load(yaml));
        assertTrue(exception.getMessage().contains("No preceding step output is assignable to step 'Foreign Branch'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains("input 'ForeignCommand'"), exception.getMessage());
    }

    @Test
    void preservesInputAlternativeThatAlreadySatisfiesDefinitionOutput() throws Exception {
        PipelineTemplateConfig config = load("""
            version: 3
            appName: Retained Result Admission
            basePackage: com.example.deployment
            transport: LOCAL
            contract: { input: Operation, output: Result }
            types:
              Operation:
                variants: { command: Command, completed: Result }
              State:
                variants: { command: Command, validated: Validated, completed: Result }
              Command: { fields: [[id, string]] }
              Validated: { fields: [[id, string]] }
              Result: { fields: [[id, string]] }
            steps:
              - { name: Validate, cardinality: ONE_TO_ONE, input: State, accepts: [Command], output: Validated }
              - { name: Complete, cardinality: ONE_TO_ONE, input: State, accepts: [Validated], output: Result }
            """);

        assertEquals("Result", config.outputContract());
        assertEquals(2, config.steps().size());
        assertFalse(config.steps().stream().anyMatch(PipelineTemplateStep::terminal));
        assertFalse(config.steps().stream().flatMap(step -> step.accepts().stream()).anyMatch("Result"::equals));
        assertFalse(config.typeModel().isAssignable("Operation", "State"));
    }

    @Test
    void rejectsForeignLeafEvenWhenAnotherInputBranchIsApplicable() {
        String yaml = lifecycle(true).replace(
            "createEnvironment: CreateEnvironmentCommand }", "createEnvironment: ForeignCommand }");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> load(yaml));
        assertTrue(exception.getMessage().contains("Pipeline input contract 'DeploymentOperation' is not assignable"),
            exception.getMessage());
    }

    @Test
    void rejectsInputUnionWithoutAnApplicableEntrySelector() {
        String yaml = lifecycle(true).replace("accepts: [CreateApplicationCommand]", "accepts: [ApplicationRecord]")
            .replace("accepts: [CreateEnvironmentCommand]", "accepts: [EnvironmentRecord]");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> load(yaml));
        assertTrue(exception.getMessage().contains("no applicable entry consumer"), exception.getMessage());
        assertTrue(exception.getMessage().contains("CreateApplicationCommand"), exception.getMessage());
        assertTrue(exception.getMessage().contains("CreateEnvironmentCommand"), exception.getMessage());
    }

    @Test
    void rejectsPartiallyUncoveredInputUnion() {
        String yaml = lifecycle(true).replace("accepts: [CreateEnvironmentCommand]", "accepts: [EnvironmentRecord]");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> load(yaml));
        assertTrue(exception.getMessage().contains("no applicable entry consumer"), exception.getMessage());
        assertTrue(exception.getMessage().contains("CreateEnvironmentCommand"), exception.getMessage());
        assertTrue(exception.getMessage().contains("concrete types [CreateEnvironmentCommand]"), exception.getMessage());
    }

    private PipelineTemplateConfig load(String yaml) throws Exception {
        Path path = tempDir.resolve("pipeline.yaml");
        Files.writeString(path, yaml);
        return new PipelineTemplateConfigLoader().load(path);
    }

    private String lifecycle(boolean named) {
        String prefix = """
            version: 3
            appName: Deployment Admission
            basePackage: com.example.deployment
            transport: LOCAL
            contract: { input: DeploymentOperation, output: CommandReceipt }
            types:
              DeploymentOperation:
                variants: { createApplication: CreateApplicationCommand, createEnvironment: CreateEnvironmentCommand }
              DeploymentState:
                variants: { createApplication: CreateApplicationCommand, validatedApplication: ValidatedApplication, applicationRecord: ApplicationRecord, createEnvironment: CreateEnvironmentCommand, validatedEnvironment: ValidatedEnvironment, environmentRecord: EnvironmentRecord }
              CreateApplicationCommand: { fields: [[id, string]] }
              CreateEnvironmentCommand: { fields: [[id, string]] }
              ValidatedApplication: { fields: [[id, string]] }
              ValidatedEnvironment: { fields: [[id, string]] }
              ApplicationRecord: { fields: [[id, string]] }
              EnvironmentRecord: { fields: [[id, string]] }
              ForeignCommand: { fields: [[id, string]] }
              CommandReceipt: { fields: [[id, string]] }
            """;
        String steps = """
            steps:
              - { name: Validate Application, cardinality: ONE_TO_ONE, input: DeploymentState, accepts: [CreateApplicationCommand], output: ValidatedApplication }
              - { name: Record Application, cardinality: ONE_TO_ONE, input: DeploymentState, accepts: [ValidatedApplication], output: ApplicationRecord }
              - { name: Validate Environment, cardinality: ONE_TO_ONE, input: DeploymentState, accepts: [CreateEnvironmentCommand], output: ValidatedEnvironment }
              - { name: Record Environment, cardinality: ONE_TO_ONE, input: DeploymentState, accepts: [ValidatedEnvironment], output: EnvironmentRecord }
              - { name: Return Receipt, cardinality: ONE_TO_ONE, input: DeploymentState, accepts: [ApplicationRecord, EnvironmentRecord], output: CommandReceipt, terminal: true }
            """;
        if (!named) {
            return prefix + steps;
        }
        return prefix + """
            pipelines:
              deployment-lifecycle:
                input: DeploymentOperation
                output: CommandReceipt
            """ + steps.indent(4) + """
            steps:
              - { name: Manage Deployment, pipeline: deployment-lifecycle, cardinality: ONE_TO_ONE, input: DeploymentOperation, output: CommandReceipt, terminal: true }
            """;
    }
}
