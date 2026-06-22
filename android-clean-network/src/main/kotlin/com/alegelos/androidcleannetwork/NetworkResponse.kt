package com.alegelos.androidcleannetwork

import java.net.URL

/** Raw data and metadata returned by [NetworkSessionProtocol]. */
data class NetworkResponse(
    val data: ByteArray,
    val statusCode: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val url: URL? = null,
)
