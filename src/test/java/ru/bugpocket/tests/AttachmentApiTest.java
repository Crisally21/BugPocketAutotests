package ru.bugpocket.tests;

import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.bugpocket.tests.api.AttachmentApi;
import ru.bugpocket.tests.api.BugApi;
import ru.bugpocket.tests.config.RequestSpecFactory;
import ru.bugpocket.tests.support.BugFixtures;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentApiTest {
    private AttachmentApi attachmentApi;
    private BugFixtures bugFixtures;

    @Test
    void shouldUploadAndDownloadPngWithoutChanges() throws IOException {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = bugFixtures.createBug("Баг со скриншотом", "HIGH");
        attachmentApi.uploadAttachment(bugId, file)
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
        int bugId = bugFixtures.createBug("Баг на PNG", "LOW");
        attachmentApi.uploadAttachment(bugId, file)
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
        int ownerBugId = bugFixtures.createBug("Баг с вложением", "HIGH");
        int anotherBugId = bugFixtures.createBug("Баг без вложения", "LOW");
        attachmentApi.uploadAttachment(ownerBugId, file)
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
        int bugId = bugFixtures.createBug("Баг с двумя вложением", "HIGH");
        attachmentApi.uploadAttachments(bugId, file, file)
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

        int bugId = bugFixtures.createBug("Баг с txt вложением", "LOW");
        attachmentApi.uploadAttachment(bugId, file)
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
        int bugId = bugFixtures.createBug("Баг с недопустимым вложением", "LOW");
        attachmentApi.uploadAttachment(bugId, file)
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
        int bugId = bugFixtures.createBug("Баг с пустым txt", "HIGH");
        attachmentApi.uploadAttachment(bugId, file)
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
        int bugId = bugFixtures.createBug("Баг с поддерживаем и не поддерживаем файлом", "HIGH");
        var response = attachmentApi.uploadAttachments(bugId, image, unsupported)
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

        int bugId = bugFixtures.createBug("Баг с недопустимым вложением", "HIGH");

        var response = attachmentApi.uploadAttachment(bugId, file, contentType)
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
        RequestSpecification requestSpec = RequestSpecFactory.create();
        BugApi bugApi = new BugApi(requestSpec);
        attachmentApi = new AttachmentApi(requestSpec);
        bugFixtures = new BugFixtures(bugApi);
    }
}
