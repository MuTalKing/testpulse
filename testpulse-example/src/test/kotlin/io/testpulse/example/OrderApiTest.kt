package io.testpulse.example

import io.qameta.allure.Attachment
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.Step
import io.qameta.allure.Story
import io.testpulse.junit.StableId
import io.testpulse.junit.TestPulseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * A realistic API test suite: Allure annotations for reporting (@Epic/@Feature/@Story/@Step),
 * request/response captured as attachments (@Attachment), TestPulse for metrics + the stable id.
 *
 * Run it, then render the report:
 *   ./gradlew :testpulse-example:test
 *   testpulse html --allure testpulse-example/build/allure-results --out report.html
 */
@Epic("Orders")
@Feature("Checkout API")
@ExtendWith(TestPulseExtension::class)
class OrderApiTest {

    @Test
    @Story("Happy path")
    @DisplayName("POST /checkout returns 200 for a valid order")
    @StableId("orders.checkout.happy")
    fun checkoutHappyPath() {
        val response = checkout("""{ "item": "widget", "qty": 2 }""")
        assertEquals(200, response.status)
        assertTrue(response.body.contains("confirmed"))
    }

    @Test
    @Story("Validation")
    @DisplayName("POST /checkout returns 400 for a negative quantity")
    @StableId("orders.checkout.validation")
    fun checkoutRejectsNegativeQuantity() {
        val response = checkout("""{ "item": "widget", "qty": -1 }""")
        assertEquals(400, response.status)
    }

    @Test
    @Story("Regression")
    @DisplayName("GET /orders/{id} returns the order")
    @StableId("orders.get_by_id")
    fun getOrderById() {
        val response = getOrder("ord-123")
        // Intentionally failing: the fake API returns 500, so the report shows the failure view
        // together with the request/response attachments and the stack trace.
        assertEquals(200, response.status, "the order endpoint should return the order")
    }

    @Step("POST /checkout")
    fun checkout(requestBody: String): Response {
        attachRequest(requestBody)
        val response = FakeOrderApi.checkout(requestBody)
        attachResponse("HTTP ${response.status}\n\n${response.body}")
        return response
    }

    @Step("GET /orders/{id}")
    fun getOrder(id: String): Response {
        attachRequest("GET /orders/$id")
        val response = FakeOrderApi.getOrder(id)
        attachResponse("HTTP ${response.status}\n\n${response.body}")
        return response
    }

    @Attachment(value = "request", type = "application/json")
    fun attachRequest(body: String): String = body

    @Attachment(value = "response", type = "application/json")
    fun attachResponse(body: String): String = body
}

data class Response(val status: Int, val body: String)

/** A tiny in-memory stand-in for the service under test. */
object FakeOrderApi {
    fun checkout(requestBody: String): Response =
        if (Regex(""""qty"\s*:\s*-""").containsMatchIn(requestBody)) {
            Response(400, """{ "error": "qty must be positive" }""")
        } else {
            Response(200, """{ "status": "confirmed", "orderId": "ord-123" }""")
        }

    fun getOrder(id: String): Response =
        Response(500, """{ "error": "internal error", "id": "$id" }""") // bug under test
}
