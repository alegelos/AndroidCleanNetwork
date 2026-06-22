package com.alegelos.androidcleannetwork.testing

/** Loads JSON fixture bytes from the classpath. */
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
