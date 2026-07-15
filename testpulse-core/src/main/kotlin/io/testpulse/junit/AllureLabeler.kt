package io.testpulse.junit

/**
 * Best-effort bridge to Allure: stamps the TestPulse `test_id` onto the current Allure test case
 * as a `testpulse.id` label, so the run report (ingested from allure-results) can join back to the
 * metric series by the exact same id — `@StableId` included.
 *
 * No-op when Allure is not on the classpath, so non-Allure projects are entirely unaffected. The
 * `available` guard runs before any reference to an Allure class, so nothing is linked unless
 * Allure is actually present.
 */
object AllureLabeler {

    const val LABEL = "testpulse.id"

    private val available: Boolean by lazy {
        runCatching { Class.forName("io.qameta.allure.Allure") }.isSuccess
    }

    fun stamp(testId: String) {
        if (!available) return
        runCatching { io.qameta.allure.Allure.label(LABEL, testId) }
    }
}
