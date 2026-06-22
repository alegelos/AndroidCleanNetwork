package com.alegelos.androidcleannetwork

/** Shared HTTP-status validator used by the default session. */
internal object NetworkValidation {
    fun validate(response: NetworkResponse): NetworkResponse = when (response.statusCode) {
        in 200..299 -> response
        400 -> throw ApiErrors.InvalidBody
        401 -> throw ApiErrors.Unauthorized
        403 -> throw ApiErrors.Forbidden
        404 -> throw ApiErrors.NotFound
        in 500..599 -> throw ApiErrors.ServerError
        else -> throw ApiErrors.Undefined
    }
}
