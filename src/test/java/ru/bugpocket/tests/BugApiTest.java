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
import ru.bugpocket.tests.api.AttachmentApi;
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
    private AttachmentApi attachmentApi;

    @Test
    void shouldReturnBugList() {
        bugApi.getBugs()
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
        bugApi.createBug(Map.of("header", "Не работает кнопка сохранения"))
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
        int bugId = createBug("Не работает кнопка сохранения", "HIGH");
        bugApi.getBugById(bugId)
              .then()
              .statusCode(200)
              .body("header", equalTo("Не работает кнопка сохранения"))
              .body("id", equalTo(bugId))
              .body("priority", equalTo("HIGH"))
              .body("status", equalTo("NEW"));
    }

    @Test
    void shouldChangeBugStatusToInProgress() {
        int bugId = createBug("Не работает кнопка сохранения", "HIGH");
        bugApi.changeBugStatus(bugId, "IN_PROGRESS")
              .then()
              .statusCode(200)
              .body("id", equalTo(bugId))
              .body("status", equalTo("IN_PROGRESS"))
              .body("header", equalTo("Не работает кнопка сохранения"))
              .body("priority", equalTo("HIGH"));
        bugApi.getBugById(bugId)
              .then()
              .statusCode(200)
              .body("id", equalTo(bugId))
              .body("status", equalTo("IN_PROGRESS"))
              .body("header", equalTo("Не работает кнопка сохранения"))
              .body("priority", equalTo("HIGH"));
    }

    @Test
    void shouldRejectInvalidStatusAndKeepBugUnchanged() {
        int bugId =
                createBug("Не работает кнопка сохранения", "HIGH");
        bugApi.changeBugStatus(bugId, "UNKNOWN")
              .then()
              .statusCode(400);
        bugApi.getBugById(bugId)
              .then()
              .statusCode(200)
              .body("id", equalTo(bugId))
              .body("status", equalTo("NEW"))
              .body("header", equalTo("Не работает кнопка сохранения"))
              .body("priority", equalTo("HIGH"));
    }

    @Test
    void shouldUpdateBugFieldAndKeepStatus() {
        int bugId = createBug("Не работает кнопка сохранения", "HIGH");
        changeBugStatus(bugId, "IN_PROGRESS");
        bugApi.updateBug(bugId, Map.of(
                "header", "Кнопка сохранения выдает ошибку",
                "priority", "LOW",
                "expectedResult", "Изменения успешно сохранены"
        ))
                .then()
                .statusCode(200)
                .body("id", equalTo(bugId))
                .body("header", equalTo("Кнопка сохранения выдает ошибку"))
                .body("priority", equalTo("LOW"))
                .body("expectedResult", equalTo("Изменения успешно сохранены"))
                .body("status", equalTo("IN_PROGRESS"));
        bugApi.getBugById(bugId)
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
        bugApi.createBug(header, "HIGH")
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
        var response = bugApi.createBug(header, "HIGH")
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
        bugApi.createBug(header, priority.name())
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
        int bugId = createBug("Новый баг", priority.name());
        bugApi.getBugs(Map.of("priority", priority.name()))
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
        bugApi.getBugs(Map.of("priority", priority, "status", status))
                .then()
                .statusCode(200)
                .body("priority", everyItem(equalTo(priority)))
                .body("status", everyItem(equalTo(status)))
                .body("id", hasItem(bugId));
    }

    @Test
    void shouldReturnLocationOfCreatedBug() {
        var response = bugApi.createBug("new bug", "HIGH")
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
        int attachmentId = attachmentApi.getAttachments(bugId)
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

        byte[] downloadedBytes = attachmentApi.downloadAttachment(bugId, attachmentId)
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
        int attachmentId = attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract()
                .jsonPath()
                .getInt("[0].id");
        attachmentApi.deleteAttachment(bugId, attachmentId)
                .then()
                .statusCode(204);
        attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
        attachmentApi.downloadAttachment(bugId, attachmentId)
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
        int attachmentId = attachmentApi.getAttachments(ownerBugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract()
                .jsonPath()
                .getInt("[0].id");
        attachmentApi.downloadAttachment(anotherBugId, attachmentId)
                .then()
                .statusCode(404);
        attachmentApi.deleteAttachment(anotherBugId, attachmentId)
                .then()
                .statusCode(404);
        attachmentApi.getAttachments(ownerBugId)
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
        var response = attachmentApi.getAttachments(bugId)
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
        attachmentApi.getAttachments(bugId)
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
        attachmentApi.getAttachments(bugId)
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
        attachmentApi.getAttachments(bugId)
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
        attachmentApi.getAttachments(bugId)
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

        attachmentApi.getAttachments(bugId)
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
        bugApi.changeBugStatus(bugId, status)
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
        attachmentApi = new AttachmentApi(requestSpec);
    }
}
