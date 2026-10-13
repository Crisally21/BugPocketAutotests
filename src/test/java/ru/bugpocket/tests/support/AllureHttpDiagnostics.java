package ru.bugpocket.tests.support;

import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public final class AllureHttpDiagnostics {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ThreadLocal<List<HttpExchange>> EXCHANGES =
            ThreadLocal.withInitial(ArrayList::new);

    private AllureHttpDiagnostics() {
    }

    public static Filter filter() {
        return (request, responseSpecification, context) -> {
            String requestDetails = formatRequest(request);
            try {
                Response response = context.next(request, responseSpecification);
                EXCHANGES.get().add(new HttpExchange(requestDetails, formatResponse(response)));
                return response;
            } catch (RuntimeException | Error exception) {
                EXCHANGES.get().add(new HttpExchange(
                        requestDetails,
                        "Ответ не получен: " + exception.getClass().getSimpleName()
                                + ": " + exception.getMessage()
                ));
                throw exception;
            }
        };
    }

    public static void clear() {
        EXCHANGES.remove();
    }

    public static void attachExchanges() {
        List<HttpExchange> exchanges = EXCHANGES.get();
        for (int index = 0; index < exchanges.size(); index++) {
            HttpExchange exchange = exchanges.get(index);
            int exchangeNumber = index + 1;
            Allure.addAttachment("HTTP запрос " + exchangeNumber, "text/plain", exchange.request());
            Allure.addAttachment("HTTP ответ " + exchangeNumber, "text/plain", exchange.response());
        }
        EXCHANGES.remove();
    }

    private static String formatRequest(FilterableRequestSpecification request) {
        String contentType = request.getHeaders().getValue("Content-Type");
        String body;
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            body = "Тело multipart-запроса не прикладывается.";
        } else {
            Object requestBody = request.getBody();
            body = formatRequestBody(requestBody);
        }

        return request.getMethod() + " " + request.getURI()
                + System.lineSeparator() + "Accept: " + request.getHeaders().getValue("Accept")
                + System.lineSeparator() + "Content-Type: " + contentType
                + System.lineSeparator() + System.lineSeparator() + body;
    }

    private static String formatRequestBody(Object requestBody) {
        if (requestBody == null) {
            return "Тело отсутствует.";
        }
        if (requestBody instanceof CharSequence) {
            return requestBody.toString();
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
        } catch (JsonProcessingException exception) {
            return requestBody.toString();
        }
    }

    private static String formatResponse(Response response) {
        String contentType = response.getContentType();
        String body;
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.startsWith("application/json")
                || normalizedContentType.contains("+json")
                || normalizedContentType.startsWith("text/")) {
            body = response.asPrettyString();
        } else {
            body = "Тело ответа не приложено (возможно, бинарное содержимое).";
        }

        return "HTTP " + response.getStatusCode()
                + System.lineSeparator() + "Content-Type: " + contentType
                + System.lineSeparator() + System.lineSeparator() + body;
    }

    private record HttpExchange(String request, String response) {
    }
}
