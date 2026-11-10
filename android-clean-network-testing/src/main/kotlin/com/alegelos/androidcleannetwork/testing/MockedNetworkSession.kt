package com.alegelos.androidcleannetwork.testing

import com.alegelos.androidcleannetwork.AccessTokenPolicy
import com.alegelos.androidcleannetwork.ApiErrors
import com.alegelos.androidcleannetwork.ApiSetupProtocol
import com.alegelos.androidcleannetwork.ApisGatewayProtocol
import com.alegelos.androidcleannetwork.NetworkResponse
import com.alegelos.androidcleannetwork.NetworkSessionProtocol

/**
 * A [NetworkSessionProtocol] that returns local JSON fixtures rather than making network calls.
 *
 * The resolver lives in test code and receives the exact production Setup built by the Provider.
 * This means tests replace only the transport boundary: no test-only Provider or Setup is needed.
 * Return a classpath path including `.json`, for example `fixtures/user.json`. A mapped fixture
 * returns status 200 and evaluates `setup.request` for its URL; JSON decoding and configurable
 * HTTP error responses are outside this fixture session's contract.
 */
class MockedNetworkSession(
    private val fixtureAnchor: Class<*>,
    private val fixtureFileNameFor: (ApiSetupProtocol) -> String?,
) : NetworkSessionProtocol {
    override suspend fun dataFor(apiRequestSetup: ApiSetupProtocol): NetworkResponse {
        val fixtureFileName = fixtureFileNameFor(apiRequestSetup)
            ?: throw Errors.FixtureNotMapped(apiRequestSetup::class.qualifiedName.orEmpty())
        return NetworkResponse(
            data = JsonReader.localJson(fixtureFileName, fixtureAnchor),
            statusCode = 200,
            url = apiRequestSetup.request.url,
        )
    }

    override suspend fun dataWithUnauthorizedRefreshRetry(
        apiAccessProvider: ApisGatewayProtocol,
        buildEndpoint: (accessToken: String) -> ApiSetupProtocol,
    ): NetworkResponse = try {
        dataFor(buildEndpoint(apiAccessProvider.apisAccessToken()))
    } catch (_: ApiErrors.Unauthorized) {
        dataFor(buildEndpoint(apiAccessProvider.apisAccessToken(AccessTokenPolicy.ForceRefresh)))
    }

    sealed class Errors(message: String) : Exception(message) {
        class FixtureNotMapped(setupType: String) : Errors("No fixture is mapped for Setup: $setupType")
    }
}
