package com.alegelos.androidcleannetwork.testing

import kotlin.test.Test
import kotlin.test.assertIs

class ProviderSpyProtocolTest {
    @Test
    fun validateFailingMethods_consumesTheFirstMatchingFailure() {
        // Given
        val spy = SampleSpy()
        spy.enqueueFailures(listOf(PlannedFailure(SampleMethod.Load, IllegalStateException("planned"))))

        // When
        val error = runCatching { spy.load() }.exceptionOrNull()

        // Then
        assertIs<IllegalStateException>(error)
        spy.load()
        spy.assertExpectedInvocations(listOf(SampleMethod.Load to 2))
    }

    private enum class SampleMethod { Load }

    private class SampleSpy : ProviderSpyProtocol<SampleMethod> {
        override val allMethods = SampleMethod.entries.toTypedArray()
        override var invocationsCount = mutableMapOf<SampleMethod, Int>()
        override var failingMethods = mutableListOf<PlannedFailure<SampleMethod>>()

        fun load() {
            increment(SampleMethod.Load)
            validateFailingMethods(SampleMethod.Load)
        }
    }
}
