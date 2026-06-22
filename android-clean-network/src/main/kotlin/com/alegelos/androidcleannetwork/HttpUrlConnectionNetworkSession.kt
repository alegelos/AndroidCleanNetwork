package com.alegelos.androidcleannetwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Opens [HttpURLConnection] instances. Injectable to support transport-level tests. */
fun interface HttpUrlConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/** The standard Android/JVM HTTP implementation. */
class HttpUrlConnectionNetworkSession(
    private val connectionFactory: HttpUrlConnectionFactory = HttpUrlConnectionFactory { url ->
        url.openConnection() as HttpURLConnection
    },
) : NetworkSessionProtocol {

    override suspend fun dataFor(apiRequestSetup: ApiSetupProtocol): NetworkResponse = withContext(Dispatchers.IO) {
        val request = apiRequestSetup.request
        var connection: HttpURLConnection? = null
        try {
            connection = connectionFactory.open(request.url)
            connection.requestMethod = request.method.value
            connection.instanceFollowRedirects = true
            request.headers.forEach { (field, value) -> connection.setRequestProperty(field, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }

            val statusCode = connection.responseCode
            val data = (if (statusCode >= 400) connection.errorStream else connection.inputStream)
                ?.use { it.readBytes() }
                ?: byteArrayOf()
            val response = NetworkResponse(
                data = data,
                statusCode = statusCode,
                headers = connection.headerFields
                    .filter { (key, value) -> key != null && value != null }
                    .map { (key, value) -> key!! to value!!.toList() }
                    .toMap(),
                url = request.url,
            )
            NetworkValidation.validate(response)
        } catch (exception: SocketTimeoutException) {
            throw ApiErrors.TimeOut
        } catch (exception: ApiErrors) {
            throw exception
        } catch (exception: Throwable) {
            throw ApiErrors.NetworkFailure(exception)
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun dataWithUnauthorizedRefreshRetry(
        apiAccessProvider: ApisGatewayProtocol,
        buildEndpoint: (accessToken: String) -> ApiSetupProtocol,
    ): NetworkResponse {
        return try {
            dataFor(buildEndpoint(apiAccessProvider.apisAccessToken()))
        } catch (_: ApiErrors.Unauthorized) {
            dataFor(buildEndpoint(apiAccessProvider.apisAccessToken(AccessTokenPolicy.ForceRefresh)))
        }
    }
}

/** Default production session, equivalent to the iOS package's `URLSession.shared`. */
object DefaultNetworkSession : NetworkSessionProtocol by HttpUrlConnectionNetworkSession()
