package com.alegelos.androidcleannetwork

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiRequestBuilderTest {
    @Test
    fun build_combinesPathHeadersBodyAndEncodedQuery() {
        // Given
        val body = "{\"name\":\"Ada\"}".encodeToByteArray()

        // When
        val actual = ApiRequestBuilder.build(
            baseUrl = URL("https://example.com/v1/"),
            path = "/users",
            method = HttpMethod.POST,
            headers = mapOf("Authorization" to "Bearer token"),
            body = body,
            queryParameters = listOf(QueryParameter("user name", "Ada Lovelace")),
        )

        // Then
        assertEquals(
            "https://example.com/v1/users?user+name=Ada+Lovelace",
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
            baseUrl = URL("https://example.com/v1?locale=en"),
            path = "user items",
            method = HttpMethod.GET,
            queryParameters = listOf(
                QueryParameter("redirect uri", "https://example.com/callback?state=one two"),
            ),
        )

        // Then
        assertEquals(
            "https://example.com/v1/user%20items?locale=en&redirect+uri=https%3A%2F%2Fexample.com%2Fcallback%3Fstate%3Done+two",
            actual.url.toExternalForm(),
        )
    }

    @Test
    fun build_preservesEscapedBasePathAndExistingQuery() {
        val actual = ApiRequestBuilder.build(
            baseUrl = URL("https://example.com/v1/users/a%2Fb%2fc/%25/%2F?cursor=a%2Fb#part%20one"),
            path = "/items with %2F?#",
            method = HttpMethod.GET,
            queryParameters = listOf(QueryParameter("name", "Ada & Lin")),
        )

        assertEquals(
            "https://example.com/v1/users/a%2Fb%2fc/%25/%2F/items%20with%20%252F%3F%23" +
                "?cursor=a%2Fb&name=Ada+%26+Lin#part%20one",
            actual.url.toExternalForm(),
        )
    }

    @Test
    fun build_preservesRepeatedBareAndEmptyQueryParameters() {
        val actual = ApiRequestBuilder.build(
            baseUrl = URL("https://example.com?tag=first"),
            path = "users",
            method = HttpMethod.GET,
            queryParameters = listOf(
                QueryParameter("tag", "café + /"),
                QueryParameter("tag", ""),
                QueryParameter("details", null),
            ),
        )

        assertEquals(
            "https://example.com/users?tag=first&tag=caf%C3%A9+%2B+%2F&tag=&details",
            actual.url.toExternalForm(),
        )
    }

    @Test
    fun build_acceptsAnEmptyEndpointAndBasePath() {
        val actual = ApiRequestBuilder.build(URL("https://example.com"), "", HttpMethod.GET)

        assertEquals("https://example.com/", actual.url.toExternalForm())
    }

    @Test
    fun build_rejectsMalformedBaseEscapes() {
        val error = runCatching {
            ApiRequestBuilder.build(URL("https://example.com/%invalid"), "users", HttpMethod.GET)
        }.exceptionOrNull()

        assertIs<ApiErrors.InvalidUrl>(error)
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
