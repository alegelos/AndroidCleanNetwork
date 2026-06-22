# README for AI agents

> If you are an AI agent given this repository URL plus endpoints, JSON examples, or API documentation, read this file before generating an Android integration.

## Goal

Generate all **human-owned integration files** needed to use `AndroidCleanNetwork` for one or more endpoints. Do not modify the framework internals unless the human explicitly asks.

Generate the Android/Kotlin equivalents of:

- `*Setup` endpoint types
- `*Provider` implementations
- Domain provider interfaces
- Domain models
- request `*DTO` values
- response `*Response` values
- response-to-Domain mapper properties
- JSON fixtures
- unit tests using `MockedNetworkSession`
- concrete provider spies
- an optional service-level spy aggregator

## Delivery rule

When the user asks to generate or scaffold an integration and provides sufficient endpoint information, produce the complete folder tree and a downloadable zip. Do not stop at an explanation or endpoint summary.

Only omit the zip if the user explicitly asks for an explanation, plan, review, architecture discussion, or partial draft. If something essential is missing, ask only for the smallest missing detail: endpoint path/method, request/response shape, authentication needed for request creation, base URL/environment, or the host project structure.

## Framework contract

The runtime artifact is `com.alegelos:android-clean-network`; the test artifact is `com.alegelos:android-clean-network-testing`.

| Framework type | Use |
| --- | --- |
| `ApiSetupProtocol` | Endpoint configuration contract. |
| `ApiRequestBuilder` | Builds encoded `NetworkRequest` values from base URL, path, headers, body, and query parameters. |
| `NetworkSessionProtocol` | Injectable transport interface. |
| `DefaultNetworkSession` | Production `HttpURLConnection` implementation. |
| `ApisGatewayProtocol` | Consumer-owned access-token provider. |
| `MockedNetworkSession` | JSON fixture transport for unit tests. |
| `FixtureSetupProtocol` | Lets a Setup select its fixture resource. |
| `ProviderSpyProtocol` | Reusable provider-spy counting and failure queue. |

## Required output structure

Mirror an existing host project when it has a clear convention. Otherwise use this structure:

```text
feature/
├── domain/
│   ├── model/
│   │   └── ExampleFeatureModel.kt
│   └── provider/
│       └── ExampleFeatureProviderProtocol.kt
├── data/
│   └── api/example/
│       ├── ExampleApiSetup.kt
│       ├── ExampleApiProvider.kt
│       ├── ExampleResponse.kt
│       └── ExampleDTO.kt
└── src/test/
    ├── kotlin/.../
    │   ├── ExampleApiProviderTest.kt
    │   ├── ExampleApiSetupFixture.kt
    │   └── ExampleProviderSpy.kt
    └── resources/fixtures/
        └── example-response.json
```

Rules:

- always create both `domain` and `data`
- Domain provider interfaces end in `Protocol` and represent a feature, not an API vendor
- concrete Providers live in Data and implement the matching Domain interface
- Domain models never expose raw transport responses
- request payloads end in `DTO`; direct server responses end in `Response`
- response mappers live beside their response and expose `val domain: DomainModel`
- use one Setup and Provider for related endpoints when that keeps the API coherent

## Setup rules

A Setup must end in `Setup`, implement `ApiSetupProtocol`, and own endpoint configuration only. It should define:

- `path`
- `method`
- `headers`
- `body`
- `queryParameters`
- `request`, made with `ApiRequestBuilder.build(...)`

Use a sealed class or sealed interface with one case/data class per related endpoint. Do not put decode, mapping, session invocation, or business decisions in a Setup.

## Provider rules

A Provider must end in `Provider`, receive `baseUrl` and `NetworkSessionProtocol`, build its Setup, execute `session.dataFor(setup)`, decode `response.data`, map to Domain, and return the Domain value.

For protected endpoints, inject `ApisGatewayProtocol` and use `dataWithUnauthorizedRefreshRetry`. The lambda must rebuild the Setup using the supplied token. Do not implement token storage or OAuth inside this framework integration.

## Tests and fixtures

Do not write a separate fake Provider for transport tests. The real Provider must be tested with `MockedNetworkSession`, the same Setup, the production decoder, and the production Domain mapper.

Create a test-only fixture-capable Setup (or adapter) that implements `FixtureSetupProtocol`:

```kotlin
override val fixtureFileName = "fixtures/example.json"
override val fixtureAnchor = ExampleApiProviderTest::class.java
```

Put fixture JSON in `src/test/resources/fixtures`. Write brief tests in Given / When / Then style and use `runTest` for suspend functions.

For behavior tests, write concrete spies by hand per Domain interface. A concrete spy implements `ProviderSpyProtocol`, supplies an enum of methods, calls `increment` and `validateFailingMethods` before forwarding each call, and exposes its count/failure state. Use service-level spy aggregators only when multiple provider spies would otherwise make tests noisy.

## Final checklist

- [ ] Setup, Provider, Domain interface, Domain model, DTO, response, and mapper are present where needed.
- [ ] Names follow `Setup`, `Provider`, `Protocol`, `DTO`, and `Response` conventions.
- [ ] Providers return Domain models, never raw responses.
- [ ] Production and test code share the exact same Provider implementation.
- [ ] Fixture files and tests are included.
- [ ] A concrete spy is included when a Domain interface needs behavioral observation.
- [ ] The generated folder tree mirrors the host project.
- [ ] Deliverable is a zip unless the user explicitly requested a non-generation response.
