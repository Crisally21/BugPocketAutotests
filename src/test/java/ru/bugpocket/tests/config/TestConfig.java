package ru.bugpocket.tests.config;

public final class TestConfig {

    private TestConfig(){
    }

    public static String baseUrl() {
        return System.getProperty(
                "baseUrl",
                "http://206.223.240.7:8080"
        );
    }

}
