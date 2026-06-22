package com.alegelos.androidcleannetwork

import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** One URL query parameter. Repeating a key is supported. */
data class QueryParameter(
    val name: String,
    val value: String?,
)

/** An immutable HTTP request assembled by a [ApiSetupProtocol]. */
data class NetworkRequest(
    val url: URL,
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

/** Helpers for building a correctly encoded request in `Setup` implementations. */
object ApiRequestBuilder {
    /**
     * Builds a request from endpoint parts. `path` can be supplied with or without a leading slash.
     * Query parameter names and values are UTF-8 encoded.
     */
    @Throws(ApiErrors.InvalidUrl::class)
    fun build(
        baseUrl: URL,
        path: String,
        method: HttpMethod,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        queryParameters: List<QueryParameter> = emptyList(),
    ): NetworkRequest {
        try {
            val baseUri = baseUrl.toURI()
            val normalisedBasePath = baseUri.path.orEmpty().trimEnd('/')
            val normalisedPath = path.trimStart('/')
            val resolvedPath = when {
                normalisedPath.isEmpty() -> normalisedBasePath.ifEmpty { "/" }
                normalisedBasePath.isEmpty() -> "/$normalisedPath"
                else -> "$normalisedBasePath/$normalisedPath"
            }
            val query = queryParameters.joinToString("&") { parameter ->
                val name = URLEncoder.encode(parameter.name, StandardCharsets.UTF_8.name())
                parameter.value?.let { value ->
                    "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
                } ?: name
            }
            val resolved = URI(
                baseUri.scheme,
                baseUri.authority,
                resolvedPath,
                query.ifEmpty { null },
                baseUri.fragment,
            )
            return NetworkRequest(resolved.toURL(), method, headers, body)
        } catch (exception: Exception) {
            if (exception is ApiErrors) throw exception
            if (exception is URISyntaxException) throw ApiErrors.InvalidUrl
            throw ApiErrors.InvalidUrl
        }
    }
}
