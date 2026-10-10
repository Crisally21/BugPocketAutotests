package ru.bugpocket.tests.api;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class AttachmentApi {
    private final RequestSpecification requestSpecification;

    public AttachmentApi(RequestSpecification requestSpecification) {
        this.requestSpecification = requestSpecification;
    }

    public Response getAttachments(int bugId) {
        return given()
                .spec(requestSpecification)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments");
    }
}
