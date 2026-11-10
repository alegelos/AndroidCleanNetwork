package com.alegelos.androidcleannetwork.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProviderSpyProtocolTest {
    @Test
    fun validateFailingMethods_consumesTheFirstMatchingFailure() {
        // Given
        val spy = UserSpy()
        spy.enqueueFailures(listOf(PlannedFailure(UserMethod.Load, IllegalStateException("planned"))))

        // When
        val error = runCatching { spy.load() }.exceptionOrNull()

        // Then
        assertIs<IllegalStateException>(error)
        spy.load()
        spy.assertExpectedInvocations(listOf(UserMethod.Load to 2))
    }

    @Test
    fun validateFailingMethods_preservesOtherMethodsAndFailureOrder() {
        val spy = UserSpy()
        val createFailure = IllegalArgumentException("create")
        val firstLoadFailure = IllegalStateException("first load")
        val secondLoadFailure = IllegalStateException("second load")
        spy.enqueueFailures(
            listOf(
                PlannedFailure(UserMethod.Create, createFailure),
                PlannedFailure(UserMethod.Load, firstLoadFailure),
                PlannedFailure(UserMethod.Load, secondLoadFailure),
            ),
        )

        assertSame(firstLoadFailure, assertFailsWith<IllegalStateException> { spy.load() })
        assertEquals(listOf(createFailure, secondLoadFailure), spy.failingMethods.map { it.error })
        assertSame(createFailure, assertFailsWith<IllegalArgumentException> { spy.create() })
        assertSame(secondLoadFailure, assertFailsWith<IllegalStateException> { spy.load() })
        spy.load()

        assertTrue(spy.failingMethods.isEmpty())
        spy.assertExpectedInvocations(listOf(UserMethod.Load to 3, UserMethod.Create to 1))
    }

    @Test
    fun resetInvocationsCount_preservesQueuedFailures() {
        val spy = UserSpy()
        val failure = IllegalStateException("planned create")
        spy.load()
        spy.enqueueFailures(listOf(PlannedFailure(UserMethod.Create, failure)))

        spy.resetInvocationsCount()

        spy.assertExpectedInvocations(emptyList())
        assertSame(failure, assertFailsWith<IllegalStateException> { spy.create() })
        spy.assertExpectedInvocations(listOf(UserMethod.Create to 1))
    }

    @Test
    fun assertExpectedInvocations_rejectsUnexpectedUnlistedCalls() {
        val spy = UserSpy()
        spy.create()

        val actual = assertFailsWith<IllegalStateException> {
            spy.assertExpectedInvocations(listOf(UserMethod.Load to 0))
        }

        assertTrue(actual.message.orEmpty().contains("Create - actual invocations: 1, expected: 0"))
    }

    @Test
    fun assertExpectedInvocations_rejectsIncorrectListedCounts() {
        val spy = UserSpy()
        spy.load()

        assertFailsWith<IllegalStateException> {
            spy.assertExpectedInvocations(listOf(UserMethod.Load to 2))
        }
    }

    @Test
    fun spies_keepTheirCountsAndFailuresIsolated() {
        val first = UserSpy()
        val second = UserSpy()
        first.load()
        first.enqueueFailures(listOf(PlannedFailure(UserMethod.Create, IllegalStateException("planned"))))

        second.assertExpectedInvocations(emptyList())
        second.create()

        second.assertExpectedInvocations(listOf(UserMethod.Create to 1))
        first.assertExpectedInvocations(listOf(UserMethod.Load to 1))
        assertEquals(1, first.failingMethods.size)
        assertTrue(second.failingMethods.isEmpty())
    }

    private enum class UserMethod { Load, Create }

    private class UserSpy : ProviderSpyProtocol<UserMethod> {
        override val allMethods = UserMethod.entries.toTypedArray()
        override var invocationsCount = mutableMapOf<UserMethod, Int>()
        override var failingMethods = mutableListOf<PlannedFailure<UserMethod>>()

        fun load() {
            increment(UserMethod.Load)
            validateFailingMethods(UserMethod.Load)
        }

        fun create() {
            increment(UserMethod.Create)
            validateFailingMethods(UserMethod.Create)
        }
    }
}
