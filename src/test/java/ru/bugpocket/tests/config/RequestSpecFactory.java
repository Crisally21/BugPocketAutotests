package ru.bugpocket.tests.config;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import ru.bugpocket.tests.support.AllureHttpDiagnostics;

public class RequestSpecFactory {
    private RequestSpecFactory() {
    }

    public static RequestSpecification create() {
        return new RequestSpecBuilder()
                .setBaseUri(TestConfig.baseUrl())
                .setAccept("application/json")
                .setConfig(RestAssuredConfig.config()
                                            .logConfig(LogConfig.logConfig()
                                                                .enableLoggingOfRequestAndResponseIfValidationFails()))
                .addFilter(AllureHttpDiagnostics.filter())
                .build();
    }
}
