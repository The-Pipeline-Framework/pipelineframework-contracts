package org.pipelineframework.runtime.spi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pipelineframework.command.CommandRequest;

class CommandRequestVisibilityTest {

    @Test
    void attemptIdFactoryIsAccessibleOutsideTheCommandPackage() {
        assertTrue(CommandRequest.newAttemptId().startsWith("attempt-"));
    }
}
