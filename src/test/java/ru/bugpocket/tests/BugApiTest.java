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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.bugpocket.tests.api.BugApi;
import ru.bugpocket.tests.config.RequestSpecFactory;
import ru.bugpocket.tests.support.BugFixtures;
import ru.bugpocket.tests.support.AllureFailureAttachmentExtension;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Feature("Баги")
@Epic("API автотесты")
@ExtendWith(AllureFailureAttachmentExtension.class)
class BugApiTest {
    private RequestSpecification requestSpec;
    private BugApi bugApi;
    private BugFixtures bugFixtures;

    @Test
    @DisplayName("Получение списка багов")
    @Story("Получение багов")
    void shouldReturnBugList() {
        Allure.step("Получить список багов и проверить ответ", () ->
                bugApi.getBugs()
                      .then()
                      .statusCode(200)
                      .contentType("application/json")
                      .body("$", instanceOf(List.class))
        );
    }

    @Test
    @DisplayName("Отказ при создании бага с пустым заголовком")
    @Story("Валидация создания бага")
    void shouldValidationCreateBug() {
        Allure.step("Отправить запрос с пустым заголовком и проверить ошибку", () ->
                bugApi.createBug("", "HIGH")
                      .then()
                      .statusCode(400)
                      .body("errors.header", notNullValue())
        );
    }

    @Test
    @DisplayName("Отказ при создании бага без приоритета")
    @Story("Валидация создания бага")
    void shouldRejectBugWithoutPriority() {
        Allure.step("Отправить запрос без приоритета и проверить ошибку", () ->
                bugApi.createBug(Map.of("header", "Не работает кнопка сохранения"))
                      .then()
                      .statusCode(400)
                      .log().body()
                      .body("errors.priority", notNullValue())
        );
    }

    @DisplayName("Создание бага с валидными данными")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Успешное создание бага")
    @Test
    void shouldCreateBugWithValidData() {
        Response response = Allure.step("Создать баг с валидными данными и проверить ответ", () ->
                bugApi.createBug("Не работает кнопка сохранения", "HIGH")
        );
        Allure.step("Проверить данные созданного бага", () -> {
            Allure.addAttachment(
                    "Ответ сервера",
                    "application/json",
                    response.asPrettyString(),
                    "json"
            );
            response.then()
                    .statusCode(201)
                    .body("header", equalTo("Не работает кнопка сохранения"))
                    .body("id", greaterThan(0))
                    .body("priority", equalTo("HIGH"))
                    .body("status", equalTo("NEW"));
        });

    }

    @Test
    @DisplayName("Получение созданного бага по идентификатору")
    @Story("Получение багов")
    void shouldReturnCreatedBugById() {
        int bugId = bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
        Allure.step("Получить созданный баг и проверить его данные", () ->
                bugApi.getBugById(bugId)
                      .then()
                      .statusCode(200)
                      .body("header", equalTo("Не работает кнопка сохранения"))
                      .body("id", equalTo(bugId))
                      .body("priority", equalTo("HIGH"))
                      .body("status", equalTo("NEW"))
        );
    }

    @Test
    @DisplayName("Изменение статуса с сохранением нового значения")
    @Story("Изменение бага")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что новый статус бага сохраняется и возвращается при повторном запросе")
    void shouldChangeBugStatusToInProgress() {
        int bugId = bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
        Allure.step(
                "Изменить статус бага на IN_PROGRESS", () -> bugApi.changeBugStatus(bugId, "IN_PROGRESS")
                                                                   .then()
                                                                   .statusCode(200)
                                                                   .body("id", equalTo(bugId))
                                                                   .body("status", equalTo("IN_PROGRESS"))
                                                                   .body("header",
                                                                         equalTo("Не работает кнопка сохранения"))
                                                                   .body("priority", equalTo("HIGH"))

        );
        Response response = Allure.step("Получить баг через GET", () -> bugApi.getBugById(bugId));
        Allure.step(
                "Проверить новый статус через GET", () -> {
                    Allure.addAttachment(
                            "Состояние бага после изменения",
                            "application/json",
                            response.asPrettyString(),
                            "json"
                    );
                    response
                            .then()
                            .statusCode(200)
                            .body("id", equalTo(bugId))
                            .body("status", equalTo("IN_PROGRESS"))
                            .body("header",
                                  equalTo("Не работает кнопка сохранения"))
                            .body("priority", equalTo("HIGH"));
                }
        );
    }

    @Test
    @DisplayName("Отказ при неизвестном статусе без изменения бага")
    @Story("Изменение бага")
    void shouldRejectInvalidStatusAndKeepBugUnchanged() {
        int bugId =
                bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
        Allure.step("Отправить неизвестный статус и проверить отказ", () ->
                bugApi.changeBugStatus(bugId, "UNKNOWN")
                      .then()
                      .statusCode(400)
        );
        Allure.step("Убедиться, что данные бага не изменились", () ->
                bugApi.getBugById(bugId)
                      .then()
                      .statusCode(200)
                      .body("id", equalTo(bugId))
                      .body("status", equalTo("NEW"))
                      .body("header", equalTo("Не работает кнопка сохранения"))
                      .body("priority", equalTo("HIGH"))
        );
    }

    @Test
    @DisplayName("Редактирование полей без изменения статуса")
    @Story("Изменение бага")
    void shouldUpdateBugFieldAndKeepStatus() {
        int bugId = bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
        bugFixtures.changeBugStatus(bugId, "IN_PROGRESS");
        Allure.step("Обновить поля бага и проверить ответ", () -> bugApi.updateBug(bugId, Map.of(
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
              .body("status", equalTo("IN_PROGRESS")));
        Allure.step("Проверить обновленные данные повторным GET", () ->
                bugApi.getBugById(bugId)
                      .then()
                      .statusCode(200)
                      .body("id", equalTo(bugId))
                      .body("header", equalTo("Кнопка сохранения выдает ошибку"))
                      .body("priority", equalTo("LOW"))
                      .body("expectedResult", equalTo("Изменения успешно сохранены"))
                      .body("status", equalTo("IN_PROGRESS"))
        );
    }

    @ParameterizedTest
    @DisplayName("Отказ при пустом заголовке")
    @Story("Валидация создания бага")
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void shouldRejectBlankHeader(String header) {
        Allure.step("Проверить отказ для пустого заголовка", () ->
                bugApi.createBug(header, "HIGH")
                      .then()
                      .statusCode(400)
                      .body("errors.header", notNullValue())
        );
    }

    @ParameterizedTest
    @DisplayName("Проверка граничной длины заголовка")
    @Story("Валидация создания бага")
    @CsvSource({
            "199, 201",
            "200, 201",
            "201, 400"
    })
    void shouldValidateHeaderLength(int length, int expectedStatus) {
        Allure.step("Проверить заголовок длиной " + length, () -> {
            String header = "A".repeat(length);
            var response = bugApi.createBug(header, "HIGH")
                                 .then()
                                 .statusCode(expectedStatus);
            if (expectedStatus == 201) {
                response.body("header", equalTo(header));
            } else {
                response.body("errors.header", notNullValue());
            }
        });
    }

    @ParameterizedTest
    @DisplayName("Создание бага с каждым приоритетом")
    @Story("Успешное создание бага")
    @Severity(SeverityLevel.CRITICAL)
    @EnumSource(Priority.class)
    void shouldCreateBugWithEachPriority(Priority priority) {
        Allure.step("Создать баг с приоритетом " + priority.name(), () -> {
            String header = "Баг с приоритетом " + priority.name();
            bugApi.createBug(header, priority.name())
                  .then()
                  .statusCode(201)
                  .body("id", greaterThan(0))
                  .body("header", equalTo(header))
                  .body("priority", equalTo(priority.name()))
                  .body("status", equalTo("NEW"));
        });
    }

    @ParameterizedTest
    @DisplayName("Фильтрация багов по приоритету")
    @Story("Фильтрация багов")
    @EnumSource(Priority.class)
    void shouldFilterBugsByPriority(Priority priority) {
        Allure.step("Отфильтровать список по приоритету " + priority.name(), () -> {
            int bugId = bugFixtures.createBug("Новый баг", priority.name());
            bugApi.getBugs(Map.of("priority", priority.name()))
                  .then()
                  .statusCode(200)
                  .body("priority", everyItem(equalTo(priority.name())))
                  .body("id", hasItem(bugId));
        });

    }

    @ParameterizedTest
    @DisplayName("Фильтрация багов по приоритету и статусу")
    @Story("Фильтрация багов")
    @CsvSource({
            "LOW, NEW",
            "MEDIUM, IN_PROGRESS",
            "HIGH, FIXED"
    })
    void shouldFilterBugsByPriorityAndStatus(String priority, String status) {
        Allure.step("Отфильтровать по приоритету " + priority + " и статусу " + status, () -> {
            int bugId = bugFixtures.createBug("Баг для проверки двух фильтров", priority);
            if (!status.equals("NEW")) {
                bugFixtures.changeBugStatus(bugId, status);
            }
            bugApi.getBugs(Map.of("priority", priority, "status", status))
                  .then()
                  .statusCode(200)
                  .body("priority", everyItem(equalTo(priority)))
                  .body("status", everyItem(equalTo(status)))
                  .body("id", hasItem(bugId));
        });
    }

    @Test
    @DisplayName("Проверка Location созданного бага")
    @Story("Успешное создание бага")
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
        Allure.step("Проверить заголовок Location", () -> assertEquals("/api/bugs/" + bugId, location));
        Allure.step("Перейти по Location и проверить созданный баг", () ->
                given()
                        .spec(requestSpec)
                        .when()
                        .get(location)
                        .then()
                        .statusCode(200)
                        .body("id", equalTo(bugId))
                        .body("header", equalTo("new bug"))
                        .body("priority", equalTo("HIGH"))
                        .body("status", equalTo("NEW"))
        );

    }

    @BeforeEach
    void setUp() {
        requestSpec = RequestSpecFactory.create();
        bugApi = new BugApi(requestSpec);
        bugFixtures = new BugFixtures(bugApi);
    }
}
