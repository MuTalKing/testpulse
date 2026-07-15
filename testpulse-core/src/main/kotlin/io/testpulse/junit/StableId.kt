package io.testpulse.junit

/**
 * Pins a test's stable identity so history survives refactors and parameterization.
 *
 * By default TestPulse derives `test_id` from `package.Class#method` + a normalized parameter key.
 * That breaks when a method is renamed or when parameterized arguments are random data. Annotating
 * a test (or class) with [StableId] makes TestPulse use this value verbatim instead, keeping the
 * metric series and run history continuous.
 *
 * ```kotlin
 * @StableId("orders.checkout.happy_path")
 * @Test
 * fun checkoutHappyPath() { ... }
 * ```
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class StableId(val value: String)
