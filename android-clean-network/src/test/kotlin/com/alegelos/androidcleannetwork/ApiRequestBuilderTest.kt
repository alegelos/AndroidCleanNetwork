package com.alegelos.androidcleannetwork

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiRequestBuilderTest {
    @Test
    fun build_combinesPathHeadersBodyAndEncodedQuery() {
        // Given
        val body = "{\"amount\":1999}".encodeToByteArray()

        // When
        val actual = ApiRequestBuilder.build(
            baseUrl = URL("https://api.example.com/v1/"),
            path = "/payment_intents",
            method = HttpMethod.POST,
            headers = mapOf("Authorization" to "Bearer token"),
            body = body,
            queryParameters = listOf(QueryParameter("customer name", "Ada Lovelace")),
        )

        // Then
        assertEquals(
            "https://api.example.com/v1/payment_intents?customer+name=Ada+Lovelace",
            actual.url.toExternalForm(),
        )
        assertEquals(HttpMethod.POST, actual.method)
        assertEquals("Bearer token", actual.headers["Authorization"])
        assertEquals(body.toList(), actual.body?.toList())
    }

    @Test
    fun build_encodesPathAndQueryOnceAndPreservesBaseQuery() {
        // When
        val actual = ApiRequestBuilder.build(
            baseUrl = URL("https://api.example.com/v1?locale=en"),
            path = "payment intents",
            method = HttpMethod.GET,
            queryParameters = listOf(
                QueryParameter("redirect uri", "https://example.com/callback?state=one two"),
            ),
        )

        // Then
        assertEquals(
            "https://api.example.com/v1/payment%20intents?locale=en&redirect+uri=https%3A%2F%2Fexample.com%2Fcallback%3Fstate%3Done+two",
            actual.url.toExternalForm(),
        )
    }

    @Test
    fun build_rejectsNonHttpUrls() {
        // When
        val error = runCatching {
            ApiRequestBuilder.build(URL("file:/tmp/fixture.json"), "profile", HttpMethod.GET)
        }.exceptionOrNull()

        // Then
        assertIs<ApiErrors.InvalidUrl>(error)
    }
}
