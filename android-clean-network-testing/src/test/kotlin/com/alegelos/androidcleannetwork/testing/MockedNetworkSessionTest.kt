package com.alegelos.androidcleannetwork.testing

import com.alegelos.androidcleannetwork.ApiRequestBuilder
import com.alegelos.androidcleannetwork.ApiSetupProtocol
import com.alegelos.androidcleannetwork.HttpMethod
import com.alegelos.androidcleannetwork.QueryParameter
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class MockedNetworkSessionTest {
    @Test
    fun provider_usesItsProductionSetupAndLoadsTheMappedFixture() = runTest {
        // Given
        val session = MockedNetworkSession(
            fixtureAnchor = MockedNetworkSessionTest::class.java,
            fixtureFileNameFor = { setup ->
                when (setup) {
                    is GitHubUserSetup -> "fixtures/github-user.json"
                    else -> null
                }
            },
        )
        val provider = GitHubUserProvider(session)

        // When
        val actual = provider.fetchUser()

        // Then
        assertEquals("octocat", actual)
    }

    private class GitHubUserProvider(
        private val session: com.alegelos.androidcleannetwork.NetworkSessionProtocol,
    ) {
        suspend fun fetchUser(): String {
            val response = session.dataFor(GitHubUserSetup())
            return "\"login\":\"(\\w+)\"".toRegex().find(response.data.decodeToString())
                ?.groupValues
                ?.get(1)
                ?: error("Unable to decode fixture")
        }
    }

    private class GitHubUserSetup : ApiSetupProtocol {
        override val path = "users/octocat"
        override val method = HttpMethod.GET
        override val headers = emptyMap<String, String>()
        override val body: ByteArray? = null
        override val queryParameters = emptyList<QueryParameter>()
        override val request = ApiRequestBuilder.build(URL("https://api.github.com"), path, method)
    }
}
