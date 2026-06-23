package com.alegelos.androidcleannetwork

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OkHttpNetworkSessionTest {
    @Test
    fun dataFor_executesTheConfiguredRequestAndReturnsResponseData() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse(code = 200, body = "ok"))
            server.start()
            val session = OkHttpNetworkSession(OkHttpClient())

            // When
            val actual = session.dataFor(TestSetup(URL(server.url("/").toString())))

            // Then
            assertEquals(200, actual.statusCode)
            assertEquals("ok", actual.data.decodeToString())
            assertEquals("/profiles/me", server.takeRequest().target)
        } finally {
            server.close()
        }
    }

    @Test
    fun dataFor_mapsHttpErrorsToApiErrors() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse(code = 401))
            server.start()
            val session = OkHttpNetworkSession(OkHttpClient())

            // When
            val error = runCatching { session.dataFor(TestSetup(URL(server.url("/").toString()))) }.exceptionOrNull()

            // Then
            assertIs<ApiErrors.Unauthorized>(error)
        } finally {
            server.close()
        }
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_refreshesOnceAfterUnauthorized() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse(code = 401))
            server.enqueue(MockResponse(code = 200, body = "success"))
            server.start()
            val seenPolicies = mutableListOf<AccessTokenPolicy>()
            val seenTokens = mutableListOf<String>()
            val session = OkHttpNetworkSession(OkHttpClient())
            val gateway = object : ApisGatewayProtocol {
                override suspend fun apisAccessToken(policy: AccessTokenPolicy): String {
                    seenPolicies += policy
                    return if (policy == AccessTokenPolicy.ForceRefresh) "fresh" else "cached"
                }
            }

            // When
            val actual = session.dataWithUnauthorizedRefreshRetry(gateway) { token ->
                seenTokens += token
                TestSetup(URL(server.url("/").toString()))
            }

            // Then
            assertEquals("success", actual.data.decodeToString())
            assertEquals(listOf(AccessTokenPolicy.Default, AccessTokenPolicy.ForceRefresh), seenPolicies)
            assertEquals(listOf("cached", "fresh"), seenTokens)
        } finally {
            server.close()
        }
    }

    private class TestSetup(baseUrl: URL) : ApiSetupProtocol {
        override val path = "profiles/me"
        override val method = HttpMethod.GET
        override val headers = emptyMap<String, String>()
        override val body: ByteArray? = null
        override val queryParameters = emptyList<QueryParameter>()
        override val request = ApiRequestBuilder.build(baseUrl, path, method)
    }
}
