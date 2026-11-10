# AndroidCleanNetwork

A Kotlin/Android networking SDK for applications that separate endpoint
configuration, transport models and Domain models. Use the same provider with
OkHttp in production and JSON fixtures in tests.

The architecture matches [iOSCleanNetwork](https://github.com/alegelos/iOSCleanNetwork),
with Kotlin `suspend` APIs and Android-specific transport and test utilities.
Examples are fictional User/Item features using `example.com`.

## Start here

- **Integrating with an AI agent:** install the
  [CleanNetwork plugin](https://github.com/alegelos/CleanNetworkPlugin) for Codex
  or Claude Code, then use it in your consuming project.
  [Installation and first request](README_AI.md#install-the-plugin-recommended).
  Copilot users can [install the standalone skill](README_AI.md#install-the-standalone-skill-alternative).
- **Integrating by hand:** read the [integration guide](skills/androidcleannetwork/references/integration.md)
  and [executable User example](skills/androidcleannetwork/assets/UserExample/).
- **Changing this SDK:** read [AGENTS.md](AGENTS.md).
- **Reviewing the audit:** see [the audit report](docs/AUDIT.md) for corrections,
  platform differences and validation boundaries.

The integration guide is the single technical reference. It ships inside the
skill so the instructions and runnable example remain together after installation.

## Requirements and artifacts

The SDK targets **Android API 23+**. This checkout builds with **JDK 17**,
**Kotlin 2.1.0**, **AGP 8.7.3**, **Gradle 8.10.2** and **Android SDK 35**.
Use the included Gradle wrapper and a compatible toolchain in the consumer app.

| Artifact | Purpose |
| --- | --- |
| `com.alegelos:android-clean-network` | Runtime contracts, request builder and OkHttp session |
| `com.alegelos:android-clean-network-testing` | JSON fixture session and provider-spy helpers |

The declared version is `1.0.1`; the instructions below use a **local checkout**.
They do not assume that this coordinate is available in a public Maven repository.

## Add the SDK to an Android project

Use a Gradle composite build pointing to your SDK checkout:

```kotlin
// settings.gradle.kts — adapt the path to your checkout
includeBuild("../AndroidCleanNetwork")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.alegelos:android-clean-network:1.0.1")
    testImplementation("com.alegelos:android-clean-network-testing:1.0.1")
}
```

Gradle substitutes the matching coordinates with projects in the included build.
Keep an existing SDK dependency if your project already has one. For local Maven
verification, `./gradlew publishToMavenLocal` publishes both modules to your local
Maven repository; then add `mavenLocal()` in the consumer's dependency repositories.
That command does not publish an external release.

### Use the release's Maven ZIP

If you prefer built artifacts, download `android-clean-network-1.0.1-maven.zip`
from the [1.0.1 release](https://github.com/alegelos/AndroidCleanNetwork/releases/tag/1.0.1).
Extract its `maven/` folder into your app's `vendor/` directory. Add this file
repository to the existing `dependencyResolutionManagement.repositories` in
`settings.gradle.kts`, alongside `google()` and `mavenCentral()`:

```kotlin
maven {
    url = uri(rootDir.resolve("vendor/maven"))
    content { includeGroup("com.alegelos") }
}
```

Use the same `1.0.1` dependencies shown above, without `includeBuild`. The ZIP
contains both release AARs, source JARs, POMs and Gradle module metadata, preserving
transitive dependencies. It is a local file repository downloaded from GitHub,
not a Maven Central publication. Keep the extracted repository available to CI.

The runtime manifest declares `INTERNET`. The application owns its network
security configuration and any exceptional cleartext policy.

## How an integration fits together

| Part | Responsibility | Example |
| --- | --- | --- |
| Domain values | Inputs and outputs used by the feature | `NewUser`, `User` |
| Domain interface | Operations expressed in Domain values | `UserProviderProtocol` |
| Setup | Endpoint URL, method, headers, query and encoded body | `UserApiSetup` |
| Request DTO | API payload representation | `CreateUserDTO` |
| Response and mapper | Decode transport data and map to Domain | `UserResponse.domain` |
| Provider | Encode input, call an injected session, decode and map | `UserApiProvider` |
| Fixture session | Load test resource bytes | `MockedNetworkSession` |

The SDK provides the transport building blocks. Your app implements the
feature-specific types and chooses its JSON codec. The User example uses
kotlinx.serialization in Data; Domain depends on neither the codec nor the SDK.

Its real provider supports `getUser(id)` and `createUser(NewUser(name))`. Both
return `User`; request DTOs and response classes stay inside Data. The standalone
example has `user-domain` and `user-data` modules to enforce that boundary. Follow
your existing app's structure when separate feature modules are unnecessary.

## Tests and JSON fixtures

Put JSON in `src/test/resources/fixtures` and configure `MockedNetworkSession`
with a test class as `fixtureAnchor` and a resolver from the real Setup to a
resource such as `fixtures/GetUser.json`. The name includes `.json`.

Tests inject that session into the production provider, preserving its codec
and mapper. Separate request tests assert the URL, method, headers, query and
payload. The fixture session builds the request to obtain its URL, but always
returns status 200 and does not validate HTTP status codes.

The example includes a handwritten provider spy for calls and planned failures.
The SDK supplies the reusable protocol; feature-specific spies belong to your app.

## Run the checks

From this repository with JDK 17 and SDK platform 35 available:

```sh
./gradlew test lint assemble --no-daemon
ANDROID_CLEAN_NETWORK_PATH="$PWD" ./gradlew \
  -p skills/androidcleannetwork/assets/UserExample \
  :user-domain:build :user-data:testDebugUnitTest \
  :user-data:lintDebug :user-data:assembleDebug --no-daemon
python3 -m unittest discover -s scripts/tests
```

Select the JDK and SDK per command with `JAVA_HOME` and `ANDROID_HOME` if needed.
The sample includes its own wrapper and also runs after copying/installing it
outside this repository; set `ANDROID_CLEAN_NETWORK_PATH` to the SDK checkout.
Python 3.9+ is needed only for the skill installer and its tests.

These are JVM unit tests and Android build/lint checks. They use fixtures and
local MockWebServer instances, without live external API calls. Device or emulator
behavior is a separate validation when the host feature needs it.

## Authentication and limits

The app supplies `ApisGatewayProtocol` for authenticated APIs. The refresh helper
requests a cached token, then forces a refresh and rebuilds the Setup once after
`ApiErrors.Unauthorized`. It supplies no token storage or OAuth implementation.

The supported HTTP methods are GET, POST, PUT and PATCH. `OkHttpNetworkSession`
validates status codes; decoding and API-specific error bodies remain the app's
responsibility. See the guide for exact signatures, request-body requirements,
error mapping, cancellation, retry boundaries and test limitations.

## AI-assisted integration

Adding a Gradle dependency does **not** install agent instructions.
For Codex and Claude Code, install the
[CleanNetwork plugin](README_AI.md#install-the-plugin-recommended) to make the
integration guides, examples and testing workflow available to your agent.
One installation includes both the iOS and Android skills. Start a new session
in your consumer project and explicitly select the Android skill for your first
request. The plugin is optional and the SDK dependency is installed separately.

For Copilot or a project-local setup, use the
[standalone skill](README_AI.md#install-the-standalone-skill-alternative).
The skill uses the open [Agent Skills format](https://agentskills.io/specification);
discovery depends on the agent client. Compilation, tests and review establish
whether generated code is correct.

## License

MIT. See [LICENSE](LICENSE).
