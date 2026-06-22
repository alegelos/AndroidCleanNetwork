package com.alegelos.androidcleannetwork

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
