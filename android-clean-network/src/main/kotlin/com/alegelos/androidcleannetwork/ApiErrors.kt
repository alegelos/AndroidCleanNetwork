package com.alegelos.androidcleannetwork

/** Errors with stable meanings returned by AndroidCleanNetwork. */
sealed class ApiErrors(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** The access token is invalid or expired. */
    data object Unauthorized : ApiErrors("Unauthorized")

    /** Refreshing the access token exceeded the consumer's allowed attempts. */
    data object TooManyRefreshAccessToken : ApiErrors("Too many access-token refreshes")

    /** The caller is authenticated but does not have permission. */
    data object Forbidden : ApiErrors("Forbidden")

    /** The requested endpoint or resource was not found. */
    data object NotFound : ApiErrors("Not found")

    /** The server returned a 5xx status code. */
    data object ServerError : ApiErrors("Server error")

    /** No more specific error can be determined. */
    data object Undefined : ApiErrors("Undefined API error")

    /** The supplied access token is malformed or invalid. */
    data object InvalidToken : ApiErrors("Invalid token")

    /** A required access token was not supplied. */
    data object MissingToken : ApiErrors("Missing token")

    /** The endpoint URL could not be built. */
    data object InvalidUrl : ApiErrors("Invalid URL")

    /** The request body is invalid. */
    data object InvalidBody : ApiErrors("Invalid request body")

    /** A response was expected but was absent. */
    data object NilResponse : ApiErrors("Missing response")

    /** The response could not be decoded by the consumer. */
    data object DecodeError : ApiErrors("Unable to decode response")

    /** The request exceeded its configured timeout. */
    data object TimeOut : ApiErrors("Request timed out")

    /** A required request parameter is missing. */
    data object MissingParameters : ApiErrors("Missing required parameters")

    /** A transport response could not be mapped into a domain model. */
    data object DomainMappingError : ApiErrors("Unable to map domain model")

    /** An unexpected transport failure, retaining the original cause. */
    class NetworkFailure(cause: Throwable) : ApiErrors("Network request failed", cause)
}
