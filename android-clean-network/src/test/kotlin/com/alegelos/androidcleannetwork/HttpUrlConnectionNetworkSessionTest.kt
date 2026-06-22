package com.alegelos.androidcleannetwork

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HttpUrlConnectionNetworkSessionTest {
    @Test
    fun dataFor_returnsDataForSuccessResponse() = runTest {
        // Given
        val session = HttpUrlConnectionNetworkSession(
            HttpUrlConnectionFactory { FakeHttpUrlConnection(it, 200, "ok".encodeToByteArray()) },
        )

        // When
        val actual = session.dataFor(TestSetup())

        // Then
        assertEquals(200, actual.statusCode)
        assertEquals("ok", actual.data.decodeToString())
    }

    @Test
    fun dataFor_mapsHttpErrorsToApiErrors() = runTest {
        // Given
        val session = HttpUrlConnectionNetworkSession(
            HttpUrlConnectionFactory { FakeHttpUrlConnection(it, 401, byteArrayOf()) },
        )

        // When
        val error = runCatching { session.dataFor(TestSetup()) }.exceptionOrNull()

        // Then
        assertIs<ApiErrors.Unauthorized>(error)
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_refreshesOnceAfterUnauthorized() = runTest {
        // Given
        val codes = ArrayDeque(listOf(401, 200))
        val seenPolicies = mutableListOf<AccessTokenPolicy>()
        val session = HttpUrlConnectionNetworkSession(
            HttpUrlConnectionFactory { url ->
                FakeHttpUrlConnection(url, codes.removeFirst(), "success".encodeToByteArray())
            },
        )
        val gateway = object : ApisGatewayProtocol {
            override suspend fun apisAccessToken(policy: AccessTokenPolicy): String {
                seenPolicies += policy
                return if (policy == AccessTokenPolicy.ForceRefresh) "fresh" else "cached"
            }
        }

        // When
        val actual = session.dataWithUnauthorizedRefreshRetry(gateway) { TestSetup() }

        // Then
        assertEquals("success", actual.data.decodeToString())
        assertEquals(listOf(AccessTokenPolicy.Default, AccessTokenPolicy.ForceRefresh), seenPolicies)
    }

    private class TestSetup : ApiSetupProtocol {
        override val path = "profiles/me"
        override val method = HttpMethod.GET
        override val headers = emptyMap<String, String>()
        override val body: ByteArray? = null
        override val queryParameters = emptyList<QueryParameter>()
        override val request = ApiRequestBuilder.build(
            baseUrl = URL("https://example.com"),
            path = path,
            method = method,
        )
    }

    private class FakeHttpUrlConnection(
        url: URL,
        private val status: Int,
        private val data: ByteArray,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(data)
        override fun getErrorStream() = ByteArrayInputStream(data)
    }
}
