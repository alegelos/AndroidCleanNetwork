package com.alegelos.androidcleannetwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

/**
 * Production [NetworkSessionProtocol] backed by OkHttp.
 *
 * Supply a shared, configured [OkHttpClient] when an app needs interceptors, TLS configuration,
 * or timeouts. The default session owns one shared client for the whole SDK.
 */
class OkHttpNetworkSession(
    private val client: OkHttpClient,
) : NetworkSessionProtocol {

    override suspend fun dataFor(apiRequestSetup: ApiSetupProtocol): NetworkResponse = withContext(Dispatchers.IO) {
        val networkRequest = apiRequestSetup.request
        val request = Request.Builder()
            .url(networkRequest.url.toExternalForm())
            .headers(networkRequest.headers.toHeaders())
            .method(networkRequest.method.value, networkRequest.body?.toRequestBody())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                NetworkValidation.validate(
                    NetworkResponse(
                        data = response.body.bytes(),
                        statusCode = response.code,
                        headers = response.headers.toMultimap(),
                        url = networkRequest.url,
                    ),
                )
            }
        } catch (exception: SocketTimeoutException) {
            throw ApiErrors.TimeOut
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ApiErrors) {
            throw exception
        } catch (exception: IOException) {
            throw ApiErrors.NetworkFailure(exception)
        }
    }

    override suspend fun dataWithUnauthorizedRefreshRetry(
        apiAccessProvider: ApisGatewayProtocol,
        buildEndpoint: (accessToken: String) -> ApiSetupProtocol,
    ): NetworkResponse = try {
        dataFor(buildEndpoint(apiAccessProvider.apisAccessToken()))
    } catch (_: ApiErrors.Unauthorized) {
        dataFor(buildEndpoint(apiAccessProvider.apisAccessToken(AccessTokenPolicy.ForceRefresh)))
    }
}

/** Default production session. It shares a single OkHttp client and its connection pool. */
object DefaultNetworkSession : NetworkSessionProtocol by OkHttpNetworkSession(OkHttpClient())
