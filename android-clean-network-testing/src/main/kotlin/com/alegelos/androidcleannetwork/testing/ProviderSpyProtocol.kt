package com.alegelos.androidcleannetwork.testing

/** A queued failure for a method observed by a [ProviderSpyProtocol]. */
data class PlannedFailure<MethodKey>(
    val method: MethodKey,
    val error: Throwable,
)

/**
 * Reusable spy behaviour for consumer-owned provider spies.
 *
 * A concrete spy supplies its enum values and calls [increment] and [validateFailingMethods]
 * at the beginning of each forwarded method, so failed calls are counted too. Supply every
 * enum value in [allMethods]. The caller owns the mutable state: isolate spies between tests
 * and serialize access when using them from multiple coroutines or threads.
 */
interface ProviderSpyProtocol<MethodKey : Enum<MethodKey>> {
    val allMethods: Array<MethodKey>
    var invocationsCount: MutableMap<MethodKey, Int>
    var failingMethods: MutableList<PlannedFailure<MethodKey>>

    fun increment(method: MethodKey) {
        invocationsCount[method] = (invocationsCount[method] ?: 0) + 1
    }

    /** Clears counters without consuming queued failures. */
    fun resetInvocationsCount() {
        invocationsCount.clear()
    }

    fun enqueueFailures(items: Iterable<PlannedFailure<MethodKey>>) {
        failingMethods.addAll(items)
    }

    /** Removes and throws the first matching failure, leaving other methods' entries queued. */
    fun validateFailingMethods(method: MethodKey) {
        val index = failingMethods.indexOfFirst { it.method == method }
        if (index >= 0) throw failingMethods.removeAt(index).error
    }

    /**
     * Checks listed counts and also verifies every unlisted method was not called.
     * This has no JUnit dependency, so it works with any Android test runner.
     */
    fun assertExpectedInvocations(expectations: Iterable<Pair<MethodKey, Int>>) {
        val expectedByMethod = expectations.toMap()
        allMethods.forEach { method ->
            val actual = invocationsCount[method] ?: 0
            val expected = expectedByMethod[method] ?: 0
            check(actual == expected) {
                "$method - actual invocations: $actual, expected: $expected"
            }
        }
    }
}
