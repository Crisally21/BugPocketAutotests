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

    public Response deleteAttachment(int bugId, int attachmentId) {
        return given()
                .spec(requestSpecification)
                .pathParam("bugId", bugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .delete("/api/bugs/{bugId}/attachments/{attachmentId}");
    }

    public Response downloadAttachment(int bugId, int attachmentId) {
        return given()
                .spec(requestSpecification)
                .accept("*/*")
                .pathParam("bugId", bugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .get("/api/bugs/{bugId}/attachments/{attachmentId}/download");
    }
}
