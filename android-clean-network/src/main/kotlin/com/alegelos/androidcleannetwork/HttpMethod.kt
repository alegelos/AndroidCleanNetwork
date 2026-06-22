package com.alegelos.androidcleannetwork

/** HTTP methods supported by an [ApiSetupProtocol]. */
enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
}
