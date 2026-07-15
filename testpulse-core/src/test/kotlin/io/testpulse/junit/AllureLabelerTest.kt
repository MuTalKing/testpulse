package io.testpulse.junit

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class AllureLabelerTest {

    @Test
    fun `stamp is a safe no-op when Allure is not on the classpath`() {
        // Allure is not a dependency of the core test runtime, so this must not throw or link
        // any Allure class.
        assertDoesNotThrow { AllureLabeler.stamp("orders.checkout") }
    }
}
