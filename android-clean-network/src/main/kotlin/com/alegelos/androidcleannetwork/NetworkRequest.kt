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

/** An HTTP request assembled by an [ApiSetupProtocol]. */
data class NetworkRequest(
    val url: URL,
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

/** Helpers for building a correctly encoded request in `Setup` implementations. */
object ApiRequestBuilder {
    /**
     * Builds a request from endpoint parts. `path` is unescaped, with `/` separating segments,
     * and can be supplied with or without a leading slash. Existing escapes in [baseUrl] are
     * preserved. Query parameter names and values are UTF-8 form-encoded.
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
            if (baseUri.scheme !in setOf("http", "https") || baseUri.rawAuthority.isNullOrEmpty()) {
                throw ApiErrors.InvalidUrl
            }
            val normalisedBasePath = baseUri.rawPath.orEmpty().trimEnd('/')
            val normalisedPath = path.trimStart('/')
            val encodedEndpointPath = URI(null, null, "/$normalisedPath", null).rawPath
            val resolvedPath = when {
                normalisedPath.isEmpty() -> normalisedBasePath.ifEmpty { "/" }
                else -> "$normalisedBasePath$encodedEndpointPath"
            }
            val query = queryParameters.joinToString("&") { parameter ->
                val name = URLEncoder.encode(parameter.name, StandardCharsets.UTF_8.name())
                parameter.value?.let { value ->
                    "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
                } ?: name
            }
            val allQuery = listOfNotNull(baseUri.rawQuery?.takeIf { it.isNotEmpty() }, query.ifEmpty { null })
                .joinToString("&")
                .ifEmpty { null }
            val resolved = URI(
                buildString {
                    append(baseUri.scheme)
                    append("://")
                    append(baseUri.rawAuthority!!)
                    append(resolvedPath)
                    allQuery?.let { append('?').append(it) }
                    baseUri.rawFragment?.let { append('#').append(it) }
                },
            )
            return NetworkRequest(resolved.toURL(), method, headers, body)
        } catch (exception: Exception) {
            if (exception is ApiErrors) throw exception
            if (exception is URISyntaxException) throw ApiErrors.InvalidUrl
            throw ApiErrors.InvalidUrl
        }
    }
}
