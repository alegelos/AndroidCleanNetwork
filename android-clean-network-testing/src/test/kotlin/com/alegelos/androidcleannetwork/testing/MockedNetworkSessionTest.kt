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
    fun dataFor_loadsTheFixtureSelectedBySetup() = runTest {
        // Given
        val session = MockedNetworkSession()

        // When
        val actual = session.dataFor(FixtureSetup())

        // Then
        assertEquals("{\"login\":\"octocat\"}\n", actual.data.decodeToString())
        assertEquals(200, actual.statusCode)
    }

    private class FixtureSetup : ApiSetupProtocol, FixtureSetupProtocol {
        override val fixtureFileName = "fixtures/github-user.json"
        override val fixtureAnchor = MockedNetworkSessionTest::class.java
        override val path = "users/octocat"
        override val method = HttpMethod.GET
        override val headers = emptyMap<String, String>()
        override val body: ByteArray? = null
        override val queryParameters = emptyList<QueryParameter>()
        override val request = ApiRequestBuilder.build(URL("https://api.github.com"), path, method)
    }
}
