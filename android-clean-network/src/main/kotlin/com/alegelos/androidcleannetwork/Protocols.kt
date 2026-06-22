package com.alegelos.androidcleannetwork

import java.net.URL

/** Shared dependencies for a provider that performs API calls. */
interface ApiProvider {
    val baseUrl: URL
    val session: NetworkSessionProtocol
}

/**
 * Endpoint configuration contract. A `Setup` owns only URL, method, headers, body and query setup.
 */
interface ApiSetupProtocol {
    val request: NetworkRequest
    val path: String
    val method: HttpMethod
    val headers: Map<String, String>
    val body: ByteArray?
    val queryParameters: List<QueryParameter>
}

/** Supplies OAuth access tokens; authentication implementation belongs to the consumer. */
interface ApisGatewayProtocol {
    suspend fun apisAccessToken(policy: AccessTokenPolicy = AccessTokenPolicy.Default): String
}

/** Controls whether a cached access token may be reused. */
sealed interface AccessTokenPolicy {
    data class UseCache(val minTtlSeconds: Long) : AccessTokenPolicy
    data object ForceRefresh : AccessTokenPolicy

    companion object {
        /** A cached token that remains valid for at least three minutes. */
        val Default: AccessTokenPolicy = UseCache(minTtlSeconds = 180)
    }
}

/** Injectable transport used by Providers and test doubles. */
interface NetworkSessionProtocol {
    suspend fun dataFor(apiRequestSetup: ApiSetupProtocol): NetworkResponse

    suspend fun dataWithUnauthorizedRefreshRetry(
        apiAccessProvider: ApisGatewayProtocol,
        buildEndpoint: (accessToken: String) -> ApiSetupProtocol,
    ): NetworkResponse
}
