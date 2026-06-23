package com.alegelos.androidcleannetwork

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkValidationTest {
    @Test
    fun validate_mapsNonSuccessStatusCodesToStableApiErrors() {
        assertStatus(400, ApiErrors.InvalidBody)
        assertStatus(401, ApiErrors.Unauthorized)
        assertStatus(403, ApiErrors.Forbidden)
        assertStatus(404, ApiErrors.NotFound)
        assertStatus(500, ApiErrors.ServerError)
        assertStatus(599, ApiErrors.ServerError)
        assertStatus(429, ApiErrors.Undefined)
    }

    private fun assertStatus(statusCode: Int, expected: ApiErrors) {
        val actual = runCatching {
            NetworkValidation.validate(NetworkResponse(byteArrayOf(), statusCode))
        }.exceptionOrNull()
        assertEquals(expected, actual)
    }
}
