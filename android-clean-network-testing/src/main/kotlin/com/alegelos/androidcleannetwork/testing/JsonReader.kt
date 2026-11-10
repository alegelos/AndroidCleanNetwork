package com.alegelos.androidcleannetwork.testing

/**
 * Loads fixture bytes from the anchor class's classpath, without decoding JSON.
 * Names are resource paths such as `fixtures/user.json`, including the extension and without
 * a leading slash. The opened stream is closed after reading or a read failure.
 */
object JsonReader {
    @Throws(Errors::class)
    fun localJson(fileName: String?, anchor: Class<*>): ByteArray {
        val name = fileName ?: throw Errors.NilFileName
        return anchor.classLoader
            ?.getResourceAsStream(name)
            ?.use { it.readBytes() }
            ?: throw Errors.FileNameNotFound(name)
    }

    sealed class Errors(message: String) : Exception(message) {
        data object NilFileName : Errors("Fixture file name is required")
        class FileNameNotFound(fileName: String) : Errors("Fixture not found: $fileName")
    }
}
