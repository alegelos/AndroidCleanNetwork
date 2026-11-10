package com.alegelos.androidcleannetwork.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonReaderTest {
    @Test
    fun localJson_loadsTheExactClasspathResource() {
        val actual = JsonReader.localJson("fixtures/user.json", JsonReaderTest::class.java)

        assertEquals("{\"id\":1,\"name\":\"Ada\"}", actual.decodeToString().trim())
    }

    @Test
    fun localJson_requiresAName() {
        assertFailsWith<JsonReader.Errors.NilFileName> {
            JsonReader.localJson(null, JsonReaderTest::class.java)
        }
    }

    @Test
    fun localJson_reportsMissingResourcesWithoutAddingAnExtension() {
        for (name in listOf("fixtures/missing-user.json", "fixtures/user", "/fixtures/user.json")) {
            val actual = assertFailsWith<JsonReader.Errors.FileNameNotFound> {
                JsonReader.localJson(name, JsonReaderTest::class.java)
            }
            assertEquals("Fixture not found: $name", actual.message)
        }
    }
}
