package com.alegelos.androidcleannetwork.testing

import com.alegelos.androidcleannetwork.AccessTokenPolicy
import com.alegelos.androidcleannetwork.ApiErrors
import com.alegelos.androidcleannetwork.ApiRequestBuilder
import com.alegelos.androidcleannetwork.ApiSetupProtocol
import com.alegelos.androidcleannetwork.ApisGatewayProtocol
import com.alegelos.androidcleannetwork.HttpMethod
import com.alegelos.androidcleannetwork.NetworkSessionProtocol
import com.alegelos.androidcleannetwork.QueryParameter
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MockedNetworkSessionTest {
    @Test
    fun provider_usesItsSetupAndLoadsTheMappedFixture() = runTest {
        val receivedSetups = mutableListOf<ApiSetupProtocol>()
        val session = session { setup ->
            receivedSetups += setup
            if (setup is UserSetup) "fixtures/user.json" else null
        }

        val actual = UserProvider(session).fetchUser()

        assertEquals("Ada", actual)
        val setup = assertIs<UserSetup>(receivedSetups.single())
        assertEquals("users/1", setup.path)
        assertEquals(1, setup.requestsBuilt)
    }

    @Test
    fun dataFor_passesTheSameSetupAndBuildsItsRequestForResponseMetadata() = runTest {
        val setup = UserSetup()
        val session = session { actual ->
            assertSame(setup, actual)
            "fixtures/user.json"
        }

        val actual = session.dataFor(setup)

        assertEquals(200, actual.statusCode)
        assertEquals("https://example.com/users/1", actual.url?.toExternalForm())
        assertEquals("{\"id\":1,\"name\":\"Ada\"}", actual.data.decodeToString().trim())
        assertTrue(actual.headers.isEmpty())
        assertEquals(1, setup.requestsBuilt)
    }

    @Test
    fun dataFor_reportsAnUnmappedSetup() = runTest {
        val actual = assertFailsWith<MockedNetworkSession.Errors.FixtureNotMapped> {
            session { null }.dataFor(UserSetup())
        }

        assertTrue(actual.message.orEmpty().contains("UserSetup"))
    }

    @Test
    fun dataFor_reportsAMissingMappedResource() = runTest {
        assertFailsWith<JsonReader.Errors.FileNameNotFound> {
            session { "fixtures/missing-user.json" }.dataFor(UserSetup())
        }
    }

    @Test
    fun dataFor_propagatesRequestConstructionErrors() = runTest {
        assertFailsWith<ApiErrors.InvalidUrl> {
            session { "fixtures/user.json" }.dataFor(UserSetup(requestFailure = ApiErrors.InvalidUrl))
        }
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_rebuildsTheSetupWithTheRefreshedToken() = runTest {
        val policies = mutableListOf<AccessTokenPolicy>()
        val seenHeaders = mutableListOf<String?>()
        val gateway = object : ApisGatewayProtocol {
            override suspend fun apisAccessToken(policy: AccessTokenPolicy): String {
                policies += policy
                return if (policy == AccessTokenPolicy.ForceRefresh) "fresh" else "cached"
            }
        }
        val session = session { setup ->
            seenHeaders += setup.headers["Authorization"]
            if (seenHeaders.size == 1) throw ApiErrors.Unauthorized
            "fixtures/user.json"
        }

        val actual = session.dataWithUnauthorizedRefreshRetry(gateway) { token ->
            UserSetup(headers = mapOf("Authorization" to "Bearer $token"))
        }

        assertEquals(200, actual.statusCode)
        assertEquals(listOf<String?>("Bearer cached", "Bearer fresh"), seenHeaders)
        assertEquals(listOf(AccessTokenPolicy.Default, AccessTokenPolicy.ForceRefresh), policies)
    }

    @Test
    fun dataWithUnauthorizedRefreshRetry_propagatesASecondUnauthorizedFailure() = runTest {
        var tokenCalls = 0
        var setupCalls = 0
        val gateway = object : ApisGatewayProtocol {
            override suspend fun apisAccessToken(policy: AccessTokenPolicy): String {
                tokenCalls++
                return "token"
            }
        }
        val session = session { throw ApiErrors.Unauthorized }

        assertFailsWith<ApiErrors.Unauthorized> {
            session.dataWithUnauthorizedRefreshRetry(gateway) {
                setupCalls++
                UserSetup()
            }
        }

        assertEquals(2, tokenCalls)
        assertEquals(2, setupCalls)
    }

    private fun session(resolver: (ApiSetupProtocol) -> String?) =
        MockedNetworkSession(MockedNetworkSessionTest::class.java, resolver)

    private class UserProvider(private val session: NetworkSessionProtocol) {
        suspend fun fetchUser(): String {
            val response = session.dataFor(UserSetup())
            return "\"name\":\"(\\w+)\"".toRegex().find(response.data.decodeToString())
                ?.groupValues
                ?.get(1)
                ?: error("Unable to decode fixture")
        }
    }

    private class UserSetup(
        override val headers: Map<String, String> = emptyMap(),
        private val requestFailure: Throwable? = null,
    ) : ApiSetupProtocol {
        var requestsBuilt = 0
            private set
        override val path = "users/1"
        override val method = HttpMethod.GET
        override val body: ByteArray? = null
        override val queryParameters = emptyList<QueryParameter>()
        override val request
            get() = run {
                requestsBuilt++
                requestFailure?.let { throw it }
                ApiRequestBuilder.build(URL("https://example.com"), path, method, headers)
            }
    }
}
