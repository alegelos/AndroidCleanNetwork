package com.alegelos.androidcleannetwork.testing

import com.alegelos.androidcleannetwork.AccessTokenPolicy
import com.alegelos.androidcleannetwork.ApiErrors
import com.alegelos.androidcleannetwork.ApiSetupProtocol
import com.alegelos.androidcleannetwork.ApisGatewayProtocol
import com.alegelos.androidcleannetwork.NetworkResponse
import com.alegelos.androidcleannetwork.NetworkSessionProtocol

/** A [NetworkSessionProtocol] that returns local JSON fixtures rather than making network calls. */
class MockedNetworkSession : NetworkSessionProtocol {
    override suspend fun dataFor(apiRequestSetup: ApiSetupProtocol): NetworkResponse {
        val fixtureSetup = apiRequestSetup as? FixtureSetupProtocol
            ?: throw Errors.InvalidMockedRequestSetup
        return NetworkResponse(
            data = JsonReader.localJson(fixtureSetup.fixtureFileName, fixtureSetup.fixtureAnchor),
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
        data object InvalidMockedRequestSetup : Errors(
            "MockedNetworkSession requires a Setup that implements FixtureSetupProtocol",
        )
    }
}
