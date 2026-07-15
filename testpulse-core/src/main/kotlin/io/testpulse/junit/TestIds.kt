package io.testpulse.junit

import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Derives a stable, human-readable test identity that survives across runs.
 *
 * Default: `package.Class#method`, plus `[index]` for a `@ParameterizedTest` invocation.
 * The parameter key is the invocation **index**, not the argument values — random or high-
 * cardinality data would otherwise explode the number of series.
 *
 * Renames break the default id, so [StableId] on the method (or class) overrides it:
 * `@StableId("orders.checkout")` pins the id regardless of the method name.
 */
object TestIds {

    private val INVOCATION_INDEX = Regex("""test-template-invocation:#(\d+)""")

    fun compute(context: ExtensionContext): String {
        val base = stableId(context) ?: defaultBase(context)
        val index = parameterIndex(context)
        return if (index != null) "$base[$index]" else base
    }

    private fun stableId(context: ExtensionContext): String? {
        context.testMethod.orElse(null)
            ?.getAnnotation(StableId::class.java)?.let { return it.value }
        context.testClass.orElse(null)
            ?.getAnnotation(StableId::class.java)?.let { return it.value }
        return null
    }

    private fun defaultBase(context: ExtensionContext): String {
        val className = context.testClass.map { it.name }.orElse("UnknownClass")
        val method = context.testMethod.map { it.name }.orElse(null)
        return if (method != null) "$className#$method" else className
    }

    private fun parameterIndex(context: ExtensionContext): String? =
        INVOCATION_INDEX.find(context.uniqueId)?.groupValues?.get(1)
}
