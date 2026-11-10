package com.alegelos.androidcleannetwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NetworkValidationTest {
    @Test
    fun validate_returnsSuccessfulResponsesIncludingEmptySuccess() {
        for (status in listOf(200, 201, 204, 299)) {
            val response = NetworkResponse(byteArrayOf(), status)
            assertSame(response, NetworkValidation.validate(response))
        }
    }

    @Test
    fun validate_mapsNonSuccessStatusCodesToStableApiErrors() {
        assertStatus(400, ApiErrors.InvalidBody)
        assertStatus(401, ApiErrors.Unauthorized)
        assertStatus(403, ApiErrors.Forbidden)
        assertStatus(404, ApiErrors.NotFound)
        assertStatus(500, ApiErrors.ServerError)
        assertStatus(599, ApiErrors.ServerError)
        assertStatus(429, ApiErrors.Undefined)
        assertStatus(300, ApiErrors.Undefined)
        assertStatus(499, ApiErrors.Undefined)
        assertStatus(600, ApiErrors.Undefined)
    }

    private fun assertStatus(statusCode: Int, expected: ApiErrors) {
        val actual = runCatching {
            NetworkValidation.validate(NetworkResponse(byteArrayOf(), statusCode))
        }.exceptionOrNull()
        assertEquals(expected, actual)
    }
}
