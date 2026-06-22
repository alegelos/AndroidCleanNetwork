package com.alegelos.androidcleannetwork.testing

/**
 * Add this contract to a production `Setup` in test sources to tell [MockedNetworkSession]
 * which JSON resource to return. The same Provider then runs unchanged in the test.
 */
interface FixtureSetupProtocol {
    /** Resource path relative to `src/test/resources`, for example `fixtures/profile.json`. */
    val fixtureFileName: String

    /** Class whose class loader owns [fixtureFileName]. Usually a test class. */
    val fixtureAnchor: Class<*>
}
