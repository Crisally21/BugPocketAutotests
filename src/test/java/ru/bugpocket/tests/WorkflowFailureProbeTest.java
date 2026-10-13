package ru.bugpocket.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class WorkflowFailureProbeTest {

    @Test
    void temporaryFailureForAllureWorkFlowCheck() {
        fail("Временная проверка публикации отчёта при падении теста");
    }
}
