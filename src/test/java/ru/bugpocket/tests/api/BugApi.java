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

    public Response getBugById(int bugId) {
        return given()
                .spec(requestSpecification)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}");
    }

    public Response changeBugStatus(int bugId, String status) {
        return given()
                .spec(requestSpecification)
                .contentType("application/json")
                .pathParam("bugId", bugId)
                .body(
                        Map.of(
                                "status", status
                        )
                )
                .when()
                .patch("/api/bugs/{bugId}/status");
    }

    public Response getBugs() {
        return given()
                .spec(requestSpecification)
                .when()
                .get("/api/bugs");
    }

    public Response getBugs(Map<String, String> filters) {
        return given()
                .spec(requestSpecification)
                .queryParams(filters)
                .when()
                .get("/api/bugs");
    }

    public Response updateBug(int bugId, Map<String, Object> body) {
        return given()
                .spec(requestSpecification)
                .contentType("application/json")
                .pathParam("bugId", bugId)
                .body(body)
                .when()
                .put("/api/bugs/{bugId}");
    }

    public Response createBug(Map<String, Object> body) {
        return given()
                .spec(requestSpecification)
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/bugs");
    }

}
