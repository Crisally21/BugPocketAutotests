package ru.bugpocket.tests;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.bugpocket.tests.api.AttachmentApi;
import ru.bugpocket.tests.api.BugApi;
import ru.bugpocket.tests.config.RequestSpecFactory;
import ru.bugpocket.tests.support.BugFixtures;
import ru.bugpocket.tests.support.AllureFailureAttachmentExtension;

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

@Epic("API автотесты")
@Feature("Вложения")
@ExtendWith(AllureFailureAttachmentExtension.class)
class AttachmentApiTest {
    private AttachmentApi attachmentApi;
    private BugFixtures bugFixtures;

    @Test
    @DisplayName("Загрузка и скачивание PNG без изменения содержимого")
    @Story("Загрузка и скачивание вложений")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет метаданные PNG и совпадение скачанных байтов с исходным файлом")
    void shouldUploadAndDownloadPngWithoutChanges() throws IOException {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = Allure.step("Создать баг для вложения", () ->
                bugFixtures.createBug("Баг со скриншотом", "HIGH")
        );
        Allure.step("Загрузить PNG", () ->
                attachmentApi.uploadAttachment(bugId, file)
                        .then()
                        .statusCode(200)
        );
        int attachmentId = Allure.step("Проверить метаданные загруженного PNG", () ->
                attachmentApi.getAttachments(bugId)
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
                .getInt("[0].id")
        );

        byte[] downloadedBytes = Allure.step("Скачать PNG", () -> attachmentApi.downloadAttachment(bugId, attachmentId)
                .then()
                .statusCode(200)
                .contentType("image/png")
                .extract()
                .asByteArray());
        byte[] originalBytes = Files.readAllBytes(file.toPath());
        Allure.step("Сравнить скачанный файл с исходным", () ->
                assertArrayEquals(originalBytes, downloadedBytes,
                        "Содержимое скачанного файла отличается от оригинала")
        );

    }

    @Test
    @DisplayName("Удаление вложения делает его недоступным")
    @Story("Удаление вложений")
    void shouldDeleteAttachmentAndMakeItUnavailable() {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = bugFixtures.createBug("Баг на PNG", "LOW");
        Allure.step("Загрузить PNG", () -> attachmentApi.uploadAttachment(bugId, file)
                .then()
                .statusCode(200));
        int attachmentId = Allure.step("Получить идентификатор загруженного файла", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract()
                .jsonPath()
                .getInt("[0].id"));
        Allure.step("Удалить вложение", () -> attachmentApi.deleteAttachment(bugId, attachmentId)
                .then()
                .statusCode(204));
        Allure.step("Проверить, что список вложений пуст", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0)));
        Allure.step("Проверить, что файл больше нельзя скачать", () -> attachmentApi.downloadAttachment(bugId, attachmentId)
                .then()
                .statusCode(404));


    }

    @Test
    @DisplayName("Вложение доступно только у своего бага")
    @Story("Проверка доступа к вложениям")
    void shouldRejectAttachmentAccessThroughAnotherBug() {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int ownerBugId = bugFixtures.createBug("Баг с вложением", "HIGH");
        int anotherBugId = bugFixtures.createBug("Баг без вложения", "LOW");
        Allure.step("Загрузить файл к исходному багу", () -> attachmentApi.uploadAttachment(ownerBugId, file)
                .then()
                .statusCode(200));
        int attachmentId = Allure.step("Получить идентификатор вложения", () -> attachmentApi.getAttachments(ownerBugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract()
                .jsonPath()
                .getInt("[0].id"));
        Allure.step("Проверить отказ в скачивании через другой баг", () -> attachmentApi.downloadAttachment(anotherBugId, attachmentId)
                .then()
                .statusCode(404));
        Allure.step("Проверить отказ в удалении через другой баг", () -> attachmentApi.deleteAttachment(anotherBugId, attachmentId)
                .then()
                .statusCode(404));
        Allure.step("Убедиться, что исходное вложение осталось", () -> attachmentApi.getAttachments(ownerBugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(attachmentId)));
    }

    @Test
    @DisplayName("Загрузка двух вложений одним запросом")
    @Story("Загрузка и скачивание вложений")
    void shouldUploadTwoAttachmentsInOneRequest() {
        File file = new File("src/test/resources/Screenshot_6.png");
        assertTrue(file.isFile(), "Не найден PNG-файл: " + file.getAbsolutePath());
        int bugId = bugFixtures.createBug("Баг с двумя вложением", "HIGH");
        Allure.step("Загрузить два PNG одним запросом", () -> attachmentApi.uploadAttachments(bugId, file, file)
                .then()
                .statusCode(200));
        Response response = Allure.step("Проверить список загруженных файлов", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("originalFilename", everyItem(equalTo(file.getName())))
                .body("sizeBytes", everyItem(equalTo((int) file.length())))
                .body("contentType", everyItem(equalTo("image/png")))
                .extract()
                .response());
        int firstAttachmentId = response.jsonPath().getInt("[0].id");
        int secondAttachmentId = response.jsonPath().getInt("[1].id");
        assertNotEquals(firstAttachmentId, secondAttachmentId);

    }

    @Test
    @DisplayName("Успешная загрузка TXT")
    @Story("Валидация типов файлов")
    void shouldUploadTxtAttachment() {
        File file = new File("src/test/resources/unsupported.txt");
        assertTrue(file.isFile(), "Не найден файл: " + file.getAbsolutePath());

        int bugId = bugFixtures.createBug("Баг с txt вложением", "LOW");
        Allure.step("Загрузить TXT и проверить результат", () -> attachmentApi.uploadAttachment(bugId, file)
                .then()
                .log().body()
                .statusCode(200)
                .body("accepted.size()", equalTo(1))
                .body("accepted[0].originalFilename", equalTo(file.getName()))
                .body("accepted[0].fileType", equalTo("TXT"))
                .body("errors.size()", equalTo(0)));
        Allure.step("Проверить список вложений", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .log().body());
    }

    @Test
    @DisplayName("Отказ при загрузке неподдерживаемого CSV")
    @Story("Валидация типов файлов")
    void shouldRejectUnsupportedAttachment() {
        File file = new File("src/test/resources/unsupportedCsv.csv");
        assertTrue(file.isFile(), "Не найден файл: " + file.getAbsolutePath());
        int bugId = bugFixtures.createBug("Баг с недопустимым вложением", "LOW");
        Allure.step("Загрузить CSV и проверить ошибку валидации", () -> attachmentApi.uploadAttachment(bugId, file)
                .then()
                .statusCode(200)
                .log().body()
                .body("errors[0].filename", equalTo(file.getName()))
                .body("errors[0].message", notNullValue())
                .body("accepted.size()", equalTo(0))
                .body("errors.size()", equalTo(1))
                .body("errors[0].filename", equalTo(file.getName()))
                .body("errors[0].message", not(emptyOrNullString())));
        Allure.step("Убедиться, что неподдерживаемый файл не сохранён", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0)));
    }

    @Test
    @DisplayName("Отказ при загрузке пустого файла")
    @Story("Валидация типов файлов")
    void shouldRejectEmptyAttachment() {
        File file = new File("src/test/resources/empty.txt");
        assertTrue(file.isFile(), "Не найден файл: " + file.getAbsolutePath());
        assertEquals(0L, file.length(), "Файл должен быть пустым");
        int bugId = bugFixtures.createBug("Баг с пустым txt", "HIGH");
        Allure.step("Загрузить пустой TXT и проверить ошибку", () -> attachmentApi.uploadAttachment(bugId, file)
                .then()
                .statusCode(200)
                .body("accepted.size()", equalTo(0))
                .body("errors.size()", equalTo(1))
                .body("errors[0].filename", equalTo(file.getName()))
                .body("errors[0].message", equalTo("Файл пустой")));
        Allure.step("Убедиться, что пустой файл не сохранён", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0)));
    }

    @Test
    @DisplayName("Из смешанной загрузки сохраняется только поддерживаемый файл")
    @Story("Валидация типов файлов")
    @Description("Проверяет частичный успех: PNG принимается, CSV отклоняется")
    void shouldAcceptPngAndRejectCsvInSameBatch() {
        File image = new File("src/test/resources/Screenshot_6.png");
        File unsupported = new File("src/test/resources/unsupportedCsv.csv");
        assertTrue(image.isFile(), "Не найден файл: " + image.getAbsolutePath());
        assertTrue(unsupported.isFile(),
                   "Не найден файл: " + unsupported.getAbsolutePath());
        assertTrue(unsupported.length() > 0, "CSV не должен быть пустым");
        int bugId = bugFixtures.createBug("Баг с поддерживаем и не поддерживаем файлом", "HIGH");
        Response response = Allure.step("Загрузить PNG и CSV одним запросом", () -> attachmentApi.uploadAttachments(bugId, image, unsupported)
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
                .response());
        int attachmentId = response.jsonPath().getInt("accepted[0].id");
        Allure.step("Проверить, что сохранён только PNG", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(attachmentId))
                .body("[0].originalFilename", equalTo(image.getName())));

    }

    @ParameterizedTest
    @MethodSource("invalidAttachments")
    @DisplayName("Отказ при загрузке недопустимого файла")
    @Story("Валидация типов файлов")
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

        Response response = Allure.step("Загрузить файл и проверить ответ валидации", () -> attachmentApi.uploadAttachment(bugId, file, contentType)
                .then()
                .statusCode(200)
                .body("accepted.size()", equalTo(0))
                .body("errors.size()", equalTo(1))
                .body("errors[0].filename", equalTo(file.getName()))
                .extract()
                .response());

        Allure.step("Проверить сообщение об ошибке", () -> {
            if (expectedMessage != null) {
                response.then().body("errors[0].message", equalTo(expectedMessage));
            } else {
                response.then().body("errors[0].message", not(emptyOrNullString()));
            }
        });

        Allure.step("Убедиться, что файл не был сохранён", () -> attachmentApi.getAttachments(bugId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0)));
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
