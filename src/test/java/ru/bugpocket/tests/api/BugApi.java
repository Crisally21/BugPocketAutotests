package ru.bugpocket.tests.api;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;


public class BugApi {
    private final RequestSpecification requestSpecification;

    public BugApi(RequestSpecification requestSpecification) {
        this.requestSpecification = requestSpecification;
    }

    public Response createBug(String header, String priority) {
        return given()
                .spec(requestSpecification)
                .contentType("application/json")
                .body(Map.of(
                        "header", header,
                        "priority", priority
                ))
                .when()
                .post("/api/bugs");
    }

}
