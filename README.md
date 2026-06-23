# AndroidCleanNetwork

Kotlin/Android counterpart to [iOSCleanNetwork](https://github.com/alegelos/iOSCleanNetwork). It keeps the same Clean Architecture idea while using idiomatic Kotlin `suspend` APIs:

- a `Setup` describes one endpoint request
- a `Provider` executes that request and maps transport values to Domain values
- a session is injected, so the same Provider runs in production and unit tests
- authentication is consumer-owned and supports a one-time 401 refresh retry

The project has two Android library modules:

| Artifact | Purpose |
| --- | --- |
| `android-clean-network` | Runtime networking contracts and the OkHttp-backed production session. |
| `android-clean-network-testing` | JSON-fixture session and reusable provider-spy contract. |

## Requirements

- Android API 23+
- JDK 17
- Kotlin 2.1+
- Android Gradle Plugin 8.7+

`android-clean-network` declares Android's normal `INTERNET` permission, which is merged into a consuming app's manifest. Use HTTPS endpoints in production; if an app must call a cleartext HTTP endpoint, its own Network Security Config must explicitly permit that host.

## Add it to an Android project

Until the artifacts are published to a Maven repository, use one of these local options.

### Composite build

```kotlin
// settings.gradle.kts
includeBuild("../AndroidCleanNetwork")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.alegelos:android-clean-network:1.0.0")
    testImplementation("com.alegelos:android-clean-network-testing:1.0.0")
}
```

### Modules copied into your project

```kotlin
// settings.gradle.kts
include(":android-clean-network")
include(":android-clean-network-testing")

// app/build.gradle.kts
dependencies {
    implementation(project(":android-clean-network"))
    testImplementation(project(":android-clean-network-testing"))
}
```

### Local Maven repository

Publish both modules, then make `mavenLocal()` available to the consuming build:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    google()
    mavenCentral()
}
```

## Design conventions

### `*Setup`

Every type that defines an endpoint ends in `Setup` and implements `ApiSetupProtocol`. A Setup owns request configuration only:

- `path`
- `method`
- `headers`
- `body`
- `queryParameters`
- `request`

Examples: `GitHubUserApiSetup`, `StripeApiSetup`, and `SpotifyApiSetup`.

### `*Provider`

Every type that performs a request ends in `Provider` and implements a Domain-layer capability interface. A Provider receives its `baseUrl` and `NetworkSessionProtocol`, creates the Setup, executes it, decodes the transport response, maps to Domain, and returns a Domain model.

Examples: `GitHubUserApiProvider`, `StripeApiProvider`, and `SpotifyApiProvider`.

### Domain and transport models

Keep presenter-facing models and capability interfaces in `Domain`. Keep concrete API code, `*DTO` request payloads, `*Response` transport values, and response-to-Domain mappers in `Data`.

Suggested feature layout:

```text
feature/
├── domain/
│   ├── model/PaymentIntent.kt
│   └── provider/PaymentsProviderProtocol.kt
├── data/
│   └── api/stripe/
│       ├── StripeApiSetup.kt
│       ├── StripeApiProvider.kt
│       ├── CreatePaymentIntentDTO.kt
│       └── StripePaymentIntentResponse.kt
└── src/test/
    ├── kotlin/.../StripeApiProviderTest.kt
    └── resources/fixtures/payment-intent.json
```

## Example: GET and POST in one API

The following example deliberately leaves JSON decoding to the app. You may use kotlinx.serialization, Moshi, Gson, or another decoder. The network SDK only transports bytes.

```kotlin
// Domain/model/PaymentIntent.kt
data class PaymentIntent(
    val identifier: String,
    val status: String,
    val clientSecret: String?,
)

// Domain/provider/PaymentsProviderProtocol.kt
interface PaymentsProviderProtocol {
    suspend fun retrievePaymentIntent(secretApiKey: String, paymentIntentId: String): PaymentIntent
    suspend fun createPaymentIntent(secretApiKey: String, dto: CreatePaymentIntentDTO): PaymentIntent
}

// Data/api/stripe/CreatePaymentIntentDTO.kt
data class CreatePaymentIntentDTO(
    val amount: Int,
    val currency: String,
    val captureMethod: String,
)

// Data/api/stripe/StripePaymentIntentResponse.kt
data class StripePaymentIntentResponse(
    val id: String,
    val status: String,
    val clientSecret: String?,
) {
    val domain: PaymentIntent
        get() = PaymentIntent(id, status, clientSecret)
}
```

```kotlin
import com.alegelos.androidcleannetwork.ApiRequestBuilder
import com.alegelos.androidcleannetwork.ApiSetupProtocol
import com.alegelos.androidcleannetwork.HttpMethod
import com.alegelos.androidcleannetwork.NetworkRequest
import com.alegelos.androidcleannetwork.QueryParameter
import java.net.URL

sealed class StripeApiSetup : ApiSetupProtocol {
    abstract val baseUrl: URL
    abstract val secretApiKey: String

    data class RetrievePaymentIntent(
        override val baseUrl: URL,
        override val secretApiKey: String,
        val paymentIntentId: String,
    ) : StripeApiSetup()

    data class CreatePaymentIntent(
        override val baseUrl: URL,
        override val secretApiKey: String,
        val dto: CreatePaymentIntentDTO,
    ) : StripeApiSetup()

    override val path: String
        get() = when (this) {
            is RetrievePaymentIntent -> "payment_intents/$paymentIntentId"
            is CreatePaymentIntent -> "payment_intents"
        }

    override val method: HttpMethod
        get() = when (this) {
            is RetrievePaymentIntent -> HttpMethod.GET
            is CreatePaymentIntent -> HttpMethod.POST
        }

    override val headers: Map<String, String>
        get() = mapOf(
            "Authorization" to "Bearer $secretApiKey",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
        )

    override val body: ByteArray?
        get() = when (this) {
            is RetrievePaymentIntent -> null
            is CreatePaymentIntent -> encodeJson(dto) // owned by your JSON codec
        }

    override val queryParameters: List<QueryParameter> = emptyList()

    override val request: NetworkRequest
        get() = ApiRequestBuilder.build(
            baseUrl = baseUrl,
            path = path,
            method = method,
            headers = headers,
            body = body,
            queryParameters = queryParameters,
        )
}
```

```kotlin
import com.alegelos.androidcleannetwork.DefaultNetworkSession
import com.alegelos.androidcleannetwork.NetworkSessionProtocol
import java.net.URL

class StripeApiProvider(
    private val baseUrl: URL,
    private val session: NetworkSessionProtocol = DefaultNetworkSession,
) : PaymentsProviderProtocol {
    override suspend fun retrievePaymentIntent(
        secretApiKey: String,
        paymentIntentId: String,
    ): PaymentIntent {
        val setup = StripeApiSetup.RetrievePaymentIntent(baseUrl, secretApiKey, paymentIntentId)
        val response = session.dataFor(setup)
        return decodePaymentIntent(response.data).domain // owned by your JSON codec
    }

    override suspend fun createPaymentIntent(
        secretApiKey: String,
        dto: CreatePaymentIntentDTO,
    ): PaymentIntent {
        val setup = StripeApiSetup.CreatePaymentIntent(baseUrl, secretApiKey, dto)
        val response = session.dataFor(setup)
        return decodePaymentIntent(response.data).domain
    }
}
```

## HTTP validation and error handling

`OkHttpNetworkSession` validates every response before returning it. `DefaultNetworkSession` owns one shared `OkHttpClient` and its connection pool; inject a configured `OkHttpClient` through `OkHttpNetworkSession` when the app needs interceptors, custom TLS, or different timeouts.

| HTTP status | Error |
| --- | --- |
| 2xx | Returns `NetworkResponse` |
| 400 | `ApiErrors.InvalidBody` |
| 401 | `ApiErrors.Unauthorized` |
| 403 | `ApiErrors.Forbidden` |
| 404 | `ApiErrors.NotFound` |
| 5xx | `ApiErrors.ServerError` |
| anything else | `ApiErrors.Undefined` |

Socket timeouts become `ApiErrors.TimeOut`; unexpected I/O failures are retained as `ApiErrors.NetworkFailure(cause)`.

## Unauthorized retry and token refresh

The SDK never implements OAuth or stores credentials. Implement `ApisGatewayProtocol` in your app or authentication SDK. Its default policy requests a cached token with at least 180 seconds remaining; `ForceRefresh` asks for a new token.

```kotlin
class SampleApisGatewayProvider : ApisGatewayProtocol {
    override suspend fun apisAccessToken(policy: AccessTokenPolicy): String = when (policy) {
        is AccessTokenPolicy.UseCache -> "cached-access-token"
        AccessTokenPolicy.ForceRefresh -> "refreshed-access-token"
    }
}
```

Use `dataWithUnauthorizedRefreshRetry` when a request must be rebuilt with the current token. It retries exactly once after an `ApiErrors.Unauthorized` response.

```kotlin
class SpotifyApiProvider(
    private val baseUrl: URL,
    private val apiAccessProvider: ApisGatewayProtocol,
    private val session: NetworkSessionProtocol = DefaultNetworkSession,
) : ProfileProviderProtocol {
    override suspend fun fetchProfile(): UserProfile {
        val response = session.dataWithUnauthorizedRefreshRetry(apiAccessProvider) { token ->
            SpotifyApiSetup.Profile(baseUrl, token)
        }
        return decodeSpotifyProfile(response.data).domain
    }
}
```

## Fixture-based Provider tests

`MockedNetworkSession` replaces only the transport. The production Provider, the exact production Setup it builds, its decode path, and its Domain mapper remain exactly the same.

In test source, map the real Setup type or case to a resource. The mapping is a test-only function, so production endpoint code never imports the testing artifact or gains fixture-only properties:

```kotlin
class GitHubUserApiProviderTest {
    private val session = MockedNetworkSession(
        fixtureAnchor = GitHubUserApiProviderTest::class.java,
        fixtureFileNameFor = { setup ->
            when (setup) {
                is GitHubUserApiSetup.User -> "fixtures/github-user.json"
                else -> null
            }
        },
    )

    private val provider = GitHubUserApiProvider(
        baseUrl = URL("https://api.github.com"),
        session = session,
    )
}
```

The Provider is untouched: when it builds `GitHubUserApiSetup.User`, the test resolver selects the fixture. No test-only Setup, wrapper, or duplicated request logic exists.

Place `github-user.json` in `src/test/resources/fixtures/`, then call the real Provider:

```kotlin
@Test
fun fetchUser_returnsMappedDomainFixture() = runTest {
    // Given
    val session = MockedNetworkSession(
        fixtureAnchor = GitHubUserApiProviderTest::class.java,
        fixtureFileNameFor = { setup ->
            if (setup is GitHubUserApiSetup.User) "fixtures/github-user.json" else null
        },
    )
    val provider = GitHubUserApiProvider(
        baseUrl = URL("https://api.github.com"),
        session = session,
    )

    // When
    val actual = provider.fetchUser("octocat")

    // Then
    assertEquals("octocat", actual.login)
}
```

## ViewModel tests: keep the feature real

This structure is intended to support feature tests too. Build the real Provider with `MockedNetworkSession`, inject it through the real Domain interface into the real ViewModel, and assert the whole state transition—not just its final value:

```kotlin
// Given: real ViewModel + real Provider + fixture-backed session
viewModel.loadProfile()

// Then: loading started and stopped, the mapped model is assigned,
// error remains empty, and unrelated state has not changed.
```

That validates request construction, API-shaped fixture decoding, mapping, and ViewModel behavior while replacing only the HTTP boundary.

## Provider spies

`ProviderSpyProtocol` is a small, runner-independent base for concrete spies that belong to the consuming project. It counts method calls, queues planned failures, and asserts both expected and unexpected calls.

```kotlin
private class PaymentsProviderSpy(
    private val wrapped: PaymentsProviderProtocol,
) : PaymentsProviderProtocol, ProviderSpyProtocol<PaymentsProviderSpy.Method> {
    enum class Method { RetrievePaymentIntent, CreatePaymentIntent }

    override val allMethods = Method.entries.toTypedArray()
    override var invocationsCount = mutableMapOf<Method, Int>()
    override var failingMethods = mutableListOf<PlannedFailure<Method>>()

    override suspend fun retrievePaymentIntent(key: String, id: String): PaymentIntent {
        increment(Method.RetrievePaymentIntent)
        validateFailingMethods(Method.RetrievePaymentIntent)
        return wrapped.retrievePaymentIntent(key, id)
    }

    override suspend fun createPaymentIntent(key: String, dto: CreatePaymentIntentDTO): PaymentIntent {
        increment(Method.CreatePaymentIntent)
        validateFailingMethods(Method.CreatePaymentIntent)
        return wrapped.createPaymentIntent(key, dto)
    }
}
```

For a higher-level service, consumer-owned aggregate spies can wrap several concrete provider spies and expose `resetInvocationsCount`, `enqueueFailures`, and `assertExpectedInvocations` as one focused test API.

## Build and test

```bash
./gradlew test
./gradlew lint
./gradlew publishToMavenLocal
```

The repository includes unit tests for request construction, status validation, one-time unauthorized retry, fixture loading, and spy failure/counter behavior.

## For AI agents

If an AI agent is generating a feature integration that consumes this SDK, it should read [README_AI.md](README_AI.md) first. The framework itself is already complete; integrations should be generated on top of it rather than modifying its internals.

## License

MIT. See [LICENSE](LICENSE).
