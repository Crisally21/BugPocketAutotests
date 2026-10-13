package ru.bugpocket.tests.support;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AllureFailureAttachmentExtension implements BeforeEachCallback, AfterTestExecutionCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        AllureHttpDiagnostics.clear();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isPresent()) {
            AllureHttpDiagnostics.attachExchanges();
        } else {
            AllureHttpDiagnostics.clear();
        }
    }
}
