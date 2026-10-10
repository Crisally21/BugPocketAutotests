package ru.bugpocket.tests;


import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.bugpocket.tests.api.BugApi;
import ru.bugpocket.tests.config.RequestSpecFactory;
import ru.bugpocket.tests.support.BugFixtures;

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

class BugApiTest {
    private RequestSpecification requestSpec;
    private BugApi bugApi;
    private BugFixtures bugFixtures;

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
        int bugId = bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
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
        int bugId = bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
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
                bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
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
        int bugId = bugFixtures.createBug("Не работает кнопка сохранения", "HIGH");
        bugFixtures.changeBugStatus(bugId, "IN_PROGRESS");
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
        int bugId = bugFixtures.createBug("Новый баг", priority.name());
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

    @BeforeEach
    void setUp() {
        requestSpec = RequestSpecFactory.create();
        bugApi = new BugApi(requestSpec);
        bugFixtures = new BugFixtures(bugApi);
    }
}
