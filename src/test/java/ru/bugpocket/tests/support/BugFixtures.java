package ru.bugpocket.tests.support;

import ru.bugpocket.tests.api.BugApi;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class BugFixtures {
    private final BugApi bugApi;

    public BugFixtures(BugApi bugApi) {
        this.bugApi = bugApi;
    }

    public void changeBugStatus(int bugId, String status) {
        bugApi.changeBugStatus(bugId, status)
              .then()
              .statusCode(200)
              .body("id", equalTo(bugId))
              .body("status", equalTo(status));
    }
    public int createBug(String header, String priority) {
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

}
