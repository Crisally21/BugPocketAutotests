package ru.bugpocket.tests;


import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.bugpocket.tests.api.BugApi;
import ru.bugpocket.tests.config.TestConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BugApiTest {
    private RequestSpecification requestSpec;
    private BugApi bugApi;

    @Test
    void shouldReturnBugList() {
        given()
                .spec(requestSpec)
                .when()
                .get("/api/bugs")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", instanceOf(List.class));
    }

    @Test
    void shouldValidationCreateBug() {
        bugApi.createBug("", "HIGH")
              .then()
              .statusCode(400)
              .body("errors.header", notNullValue());
    }

    @Test
    void shouldRejectBugWithoutPriority() {
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .body("""
                              {
                              "header": "Не работает кнопка сохранения"
                              }
                              """)
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(400)
                .log().body()
                .body("errors.priority", notNullValue());
    }

    @Test
    void shouldCreateBugWithValidData() {
        bugApi.createBug("Не работает кнопка сохранения", "HIGH")
              .then()
              .statusCode(201)
              .body("header", equalTo("Не работает кнопка сохранения"))
              .body("id", greaterThan(0))
              .body("priority", equalTo("HIGH"))
              .body("status", equalTo("NEW"));
    }

    @Test
    void shouldReturnCreatedBugById() {
        int bugId = given()
                .spec(requestSpec)
                .contentType("application/json")
                .body("""
                              {
                              "header": "Не работает кнопка сохранения",
                              "priority": "HIGH"
                              }
                              """)
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getInt("id");
        given()
                .spec(requestSpec)
                .pathParam("id", bugId)
                .when()
                .get("/api/bugs/{id}")
                .then()
                .statusCode(200)
                .body("header", equalTo("Не работает кнопка сохранения"))
                .body("id", equalTo(bugId))
                .body("priority", equalTo("HIGH"))
                .body("status", equalTo("NEW"));
    }

    @Test
    void shouldChangeBugStatusToInProgress() {
        int bugId = given()
                .spec(requestSpec)
                .contentType("application/json")
                .body("""
                              {
                              "header": "Не работает кнопка сохранения",
                              "priority": "HIGH"
                              }
                              """)
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getInt("id");
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .pathParam("id", bugId)
                .body("""
                              {
                              "status": "IN_PROGRESS"
                              }
                              """)
                .when()
                .patch("/api/bugs/{id}/status")
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("header", equalTo("Не работает кнопка сохранения"))
                .body("priority", equalTo("HIGH"));
        given()
                .spec(requestSpec)
                .pathParam("id", bugId)
                .when()
                .get("/api/bugs/{id}")
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("header", equalTo("Не работает кнопка сохранения"))
                .body("priority", equalTo("HIGH"));
    }

    @Test
    void shouldRejectInvalidStatusAndKeepBugUnchanged() {
        int bugId = given()
                .spec(requestSpec)
                .contentType("application/json")
                .body("""
                              {
                              "header": "Не работает кнопка сохранения",
                              "priority": "HIGH"
                              }
                              """)
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(201)
                .body("status", equalTo("NEW"))
                .extract()
                .jsonPath()
                .getInt("id");
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .pathParam("id", bugId)
                .body("""
                              {
                              "status": "UNKNOWN"
                              }
                              """)
                .when()
                .patch("/api/bugs/{id}/status")
                .then()
                .statusCode(400);
        given()
                .spec(requestSpec)
                .pathParam("id", bugId)
                .when()
                .get("/api/bugs/{id}")
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("status", equalTo("NEW"))
                .body("header", equalTo("Не работает кнопка сохранения"))
                .body("priority", equalTo("HIGH"));
    }

    @Test
    void shouldUpdateBugFieldAndKeepStatus() {
        int bugId = given()
                .spec(requestSpec)
                .contentType("application/json")
                .body("""
                              {
                              "header": "Не работает кнопка сохранения",
                              "priority": "HIGH"
                              }
                              """)
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(201)
                .body("status", equalTo("NEW"))
                .extract()
                .jsonPath()
                .getInt("id");
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .pathParam("id", bugId)
                .body("""
                              {
                              "status": "IN_PROGRESS"
                              }
                              """)
                .when()
                .patch("/api/bugs/{id}/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .pathParam("id", bugId)
                .body("""
                              {
                              "header": "Кнопка сохранения выдает ошибку",
                              "priority": "LOW",
                              "expectedResult": "Изменения успешно сохранены"
                              }
                              """)
                .when()
                .put("/api/bugs/{id}")
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("header", equalTo("Кнопка сохранения выдает ошибку"))
                .body("priority", equalTo("LOW"))
                .body("expectedResult", equalTo("Изменения успешно сохранены"))
                .body("status", equalTo("IN_PROGRESS"));
        given()
                .spec(requestSpec)
                .pathParam("id", bugId)
                .when()
                .get("/api/bugs/{id}")
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("header", equalTo("Кнопка сохранения выдает ошибку"))
                .body("priority", equalTo("LOW"))
                .body("expectedResult", equalTo("Изменения успешно сохранены"))
                .body("status", equalTo("IN_PROGRESS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void shouldRejectBlankHeader(String header) {
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .body(Map.of(
                        "header", header,
                        "priority", "HIGH"
                ))
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(400)
                .body("errors.header", notNullValue());
    }

    @ParameterizedTest
    @CsvSource({
            "199, 201",
            "200, 201",
            "201, 400"
    })
    void shouldValidateHeaderLength(int length, int expectedStatus) {
        String header = "A".repeat(length);
        var response = given()
                .baseUri("http://206.223.240.7:8080/")
                .accept("application/json")
                .contentType("application/json")
                .body("""
                              {
                              "header": "%s",
                              "priority": "HIGH"
                              }
                              """.formatted(header)
                )
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(expectedStatus);
        if (expectedStatus == 201) {
            response.body("header", equalTo(header));
        } else {
            response.body("errors.header", notNullValue());
        }
    }

    @ParameterizedTest
    @EnumSource(Priority.class)
    void shouldCreateBugWithEachPriority(Priority priority) {
        String header = "Баг с приоритетом " + priority.name();
        given()
                .spec(requestSpec)
                .contentType("application/json")
                .body(Map.of(
                        "header", header,
                        "priority", priority.name()
                ))
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(201)
                .body("id", greaterThan(0))
                .body("header", equalTo(header))
                .body("priority", equalTo(priority.name()))
                .body("status", equalTo("NEW"));
    }

    @ParameterizedTest
    @EnumSource(Priority.class)
    void shouldFilterBugsByPriority(Priority priority) {
        int bugId =
                given()
                        .spec(requestSpec)
                        .contentType("application/json")
                        .body(Map.of(
                                "header", "Новый баг",
                                "priority", priority.name()
                        ))
                        .when()
                        .post("/api/bugs")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getInt("id");
        given()
                .spec(requestSpec)
                .queryParam("priority", priority.name())
                .when()
                .get("/api/bugs")
                .then()
                .statusCode(200)
                .body("priority", everyItem(equalTo(priority.name())))
                .body("id", hasItem(bugId));

    }

    @ParameterizedTest
    @CsvSource({
            "LOW, NEW",
            "MEDIUM, IN_PROGRESS",
            "HIGH, FIXED"
    })
    void shouldFilterBugsByPriorityAndStatus(String priority, String status) {
        int bugId = createBug("Баг для проверки двух фильтров", priority);
        if (!status.equals("NEW")) {
            changeBugStatus(bugId, status);
        }
        given()
                .spec(requestSpec)
                .queryParam("priority", priority)
                .queryParam("status", status)
                .when()
                .get("/api/bugs")
                .then()
                .statusCode(200)
                .body("priority", everyItem(equalTo(priority)))
                .body("status", everyItem(equalTo(status)))
                .body("id", hasItem(bugId));
    }

    @Test
    void shouldReturnLocationOfCreatedBug() {
        var response = given()
                .spec(requestSpec)
                .contentType("application/json")
                .body(Map.of(
                        "header", "new bug",
                        "priority", "HIGH"
                ))
                .when()
                .post("/api/bugs")
                .then()
                .statusCode(201)
                .body("id", greaterThan(0))
                .body("header", equalTo("new bug"))
                .body("priority", equalTo("HIGH"))
                .extract()
                .response();
        int bugId = response.jsonPath().getInt("id");
        String location = response.getHeader("Location");
        assertEquals("/api/bugs/" + bugId, location);
        given()
                .spec(requestSpec)
                .when()
                .get(location)
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("header", equalTo("new bug"))
                .body("priority", equalTo("HIGH"))
                .body("status", equalTo("NEW"));

    }

    @Test
    void shouldUploadAndDownloadPngWithoutChanges() throws IOException {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = createBug("Баг со скриншотом", "HIGH");
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .multiPart("files", file, "image/png")
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200);
        int attachmentId = given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].originalFilename", equalTo(file.getName()))
                .body("[0].sizeBytes", equalTo((int) file.length()))
                .body("[0].mediaKind", equalTo("IMAGE"))
                .body("[0].fileType", equalTo("PNG"))
                .body("[0].contentType", equalTo("image/png"))
                .extract()
                .jsonPath()
                .getInt("[0].id");

        byte[] downloadedBytes = given()
                .spec(requestSpec)
                .accept("image/png")
                .pathParam("bugId", bugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .get("/api/bugs/{bugId}/attachments/{attachmentId}/download")
                .then()
                .statusCode(200)
                .contentType("image/png")
                .extract()
                .asByteArray();
        byte[] originalBytes = Files.readAllBytes(file.toPath());
        assertArrayEquals(originalBytes, downloadedBytes, "Содержимое скачанного файла отличается от оригинала");

    }

    @Test
    void shouldDeleteAttachmentAndMakeItUnavailable() {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = createBug("Баг на PNG", "LOW");
        given()
                .spec(requestSpec)
                .multiPart("files", file, "image/png")
                .pathParam("bugId", bugId)
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200);
        int attachmentId = given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract()
                .jsonPath()
                .getInt("[0].id");
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .delete("/api/bugs/{bugId}/attachments/{attachmentId}")
                .then()
                .statusCode(204);
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .get("/api/bugs/{bugId}/attachments/{attachmentId}/download")
                .then()
                .statusCode(404);


    }

    @Test
    void shouldRejectAttachmentAccessThroughAnotherBug() {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int ownerBugId = createBug("Баг с вложением", "HIGH");
        int anotherBugId = createBug("Баг без вложения", "LOW");
        given()
                .spec(requestSpec)
                .multiPart("files", file, "image/png")
                .pathParam("bugId", ownerBugId)
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200);
        int attachmentId = given()
                .spec(requestSpec)
                .pathParam("bugId", ownerBugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract()
                .jsonPath()
                .getInt("[0].id");
        given()
                .spec(requestSpec)
                .pathParam("bugId", anotherBugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .get("/api/bugs/{bugId}/attachments/{attachmentId}/download")
                .then()
                .statusCode(404);
        given()
                .spec(requestSpec)
                .pathParam("bugId", anotherBugId)
                .pathParam("attachmentId", attachmentId)
                .when()
                .delete("/api/bugs/{bugId}/attachments/{attachmentId}")
                .then()
                .statusCode(404);
        given()
                .spec(requestSpec)
                .pathParam("bugId", ownerBugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(attachmentId));
    }

    @Test
    void shouldUploadTwoAttachmentsInOneRequest() {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = createBug("Баг с двумя вложением", "HIGH");
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .multiPart("files", file, "image/png")
                .multiPart("files", file, "image/png")
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200);
        var response = given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("originalFilename", everyItem(equalTo(file.getName())))
                .body("sizeBytes", everyItem(equalTo((int) file.length())))
                .body("contentType", everyItem(equalTo("image/png")))
                .extract()
                .response();
        int firstAttachmentId = response.jsonPath().getInt("[0].id");
        int secondAttachmentId = response.jsonPath().getInt("[1].id");
        assertNotEquals(firstAttachmentId, secondAttachmentId);

    }

    @Test
    void shouldUploadTxtAttachment() {
        File file = new File("src/test/resources/unsupported.txt");
        assertTrue(file.isFile(), "Не найден файл: " + file.getAbsolutePath());

        int bugId = createBug("Баг с txt вложением", "LOW");
        given()
                .spec(requestSpec)
                .multiPart("files", file, "text/plain")
                .pathParam("bugId", bugId)
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .log().body()
                .statusCode(200)
                .body("accepted.size()", equalTo(1))
                .body("accepted[0].originalFilename", equalTo(file.getName()))
                .body("accepted[0].fileType", equalTo("TXT"))
                .body("errors.size()", equalTo(0));
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .log().body();
    }

    @Test
    void shouldRejectUnsupportedAttachment() {
        File file = new File("src/test/resources/unsupportedCsv.csv");
        assertTrue(file.isFile(), "Не найден файл: " + file.getAbsolutePath());
        int bugId = createBug("Баг с недопустимым вложением", "LOW");
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .multiPart("files", file, "text/csv")
                .when()
                .post("api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .log().body()
                .body("errors[0].filename", equalTo(file.getName()))
                .body("errors[0].message", notNullValue())
                .body("accepted.size()", equalTo(0))
                .body("errors.size()", equalTo(1))
                .body("errors[0].filename", equalTo(file.getName()))
                .body("errors[0].message", not(emptyOrNullString()));
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void shouldRejectEmptyAttachment() {
        File file = new File("src/test/resources/empty.txt");
        assertTrue(file.isFile(), "Не найден файл: " + file.getAbsolutePath());
        assertEquals(0L, file.length(), "Файл должен быть пустым");
        int bugId = createBug("Баг с пустым txt", "HIGH");
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .multiPart("files", file, "text/plain")
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("accepted.size()", equalTo(0))
                .body("errors.size()", equalTo(1))
                .body("errors[0].filename", equalTo(file.getName()))
                .body("errors[0].message", equalTo("Файл пустой"));
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void shouldAcceptPngAndRejectCsvInSameBatch() {
        File image = new File("src/test/resources/Screenshot_6.png");
        File unsupported = new File("src/test/resources/unsupportedCsv.csv");
        assertTrue(image.isFile(), "Не найден файл: " + image.getAbsolutePath());
        assertTrue(unsupported.isFile(),
                   "Не найден файл: " + unsupported.getAbsolutePath());
        assertTrue(unsupported.length() > 0, "CSV не должен быть пустым");
        int bugId = createBug("Баг с поддерживаем и не поддерживаем файлом", "HIGH");
        var response = given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .multiPart("files", image, "image/png")
                .multiPart("files", unsupported, "text/csv")
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("accepted.size()", equalTo(1))
                .body("accepted[0].originalFilename", equalTo(image.getName()))
                .body("accepted[0].fileType", equalTo("PNG"))
                .body("errors.size()", equalTo(1))
                .body("errors[0].message", not(emptyOrNullString()))
                .body("errors[0].filename", equalTo(unsupported.getName()))
                .body("errors[0].message", notNullValue())
                .extract()
                .response();
        int attachmentId = response.jsonPath().getInt("accepted[0].id");
        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(attachmentId))
                .body("[0].originalFilename", equalTo(image.getName()));

    }

    @ParameterizedTest
    @MethodSource("invalidAttachments")
    void shouldRejectInvalidAttachment(
            File file,
            String contentType,
            String expectedMessage
    ) {
        assertTrue(file.isFile(),
                   "Не найден файл: " + file.getAbsolutePath());

        if (expectedMessage != null) {
            assertEquals(0L, file.length(), "Файл должен быть пустым");
        } else {
            assertTrue(file.length() > 0, "CSV не должен быть пустым");
        }

        int bugId = createBug("Баг с недопустимым вложением", "HIGH");

        var response = given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .multiPart("files", file, contentType)
                .when()
                .post("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("accepted.size()", equalTo(0))
                .body("errors.size()", equalTo(1))
                .body("errors[0].filename", equalTo(file.getName()));

        if (expectedMessage != null) {
            response.body("errors[0].message", equalTo(expectedMessage));
        } else {
            response.body("errors[0].message", not(emptyOrNullString()));
        }

        given()
                .spec(requestSpec)
                .pathParam("bugId", bugId)
                .when()
                .get("/api/bugs/{bugId}/attachments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }


    private int createBug(String header, String priority) {
        return bugApi.createBug(header, priority)
                     .then()
                     .statusCode(201)
                     .body("id", greaterThan(0))
                     .body("header", equalTo(header))
                     .body("priority", equalTo(priority))
                     .body("status", equalTo("NEW"))
                     .extract()
                     .jsonPath()
                     .getInt("id");
    }

    private void changeBugStatus(int bugId, String status) {
        given()
                .spec(requestSpec)
                .pathParam("id", bugId)
                .contentType("application/json")
                .body(Map.of(
                        "status", status
                ))
                .when()
                .patch("/api/bugs/{id}/status")
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("status", equalTo(status));
    }

    static Stream<Arguments> invalidAttachments() {
        return Stream.of(
                Arguments.of(
                        new File("src/test/resources/empty.txt"),
                        "text/plain",
                        "Файл пустой"
                ),
                Arguments.of(
                        new File("src/test/resources/unsupportedCsv.csv"),
                        "text/csv",
                        null
                )
        );
    }

    @BeforeEach
    void setUp() {
        requestSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.baseUrl())
                .setAccept("application/json")
                .setConfig(RestAssuredConfig.config()
                                            .logConfig(LogConfig.logConfig()
                                                                .enableLoggingOfRequestAndResponseIfValidationFails()))
                .build();
        bugApi = new BugApi(requestSpec);
    }
}
