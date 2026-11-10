# Android SDK and agent integration audit

Audit date: 2026-11-10. Baseline: `cd0d398e13fe7ef1eaa8805a7a7a2040b0349e28`.

Scope: the runtime and testing modules, Android/iOS conceptual parity, consumer
documentation, a portable Agent Skill, its executable example and validation.
Public SDK signatures, artifact coordinates and dependency versions are preserved.
The audit started from locally declared version 1.0.0. Its corrections and agent
guidance ship in 1.0.1; publication status is tracked in the release, separately
from the local validation recorded below.

## Corrected defects

| Finding | Observable problem | Correction and regression |
| --- | --- | --- |
| Escaped base path was decoded before joining | A base segment containing `%2F` became multiple path segments, changing the target URL | Keep the base URI's raw path and encode only the new endpoint path; assert exact escaped path/query/fragment preservation |
| Coroutine cancellation did not cancel the OkHttp call | A cancelled caller left an HTTP request waiting for headers or reading a body | Register `Call.cancel()` across the entire execute/read/close operation; exercise both phases against a local server |
| Total call timeout was classified as a generic I/O error | OkHttp's `InterruptedIOException("timeout")` became `NetworkFailure` | Map the known timeout form and socket timeouts to `TimeOut`; preserve other I/O failures and their causes |

The URL and total-timeout regressions failed against the original implementation.
The two cancellation regressions also failed with an explicit assertion that the
active call had not been cancelled. Test cleanup uses the captured call directly,
so a stalled body cannot hide that assertion behind a later socket timeout.
JUnit blocking-coroutine tests declare `Unit` explicitly to retain discoverable
void test methods.

The transport still runs on `Dispatchers.IO`. Responses close before their byte
data crosses the continuation boundary. The existing one-refresh retry policy is
retained, including an initial `Unauthorized` from token acquisition or setup
construction. Retry coverage checks regenerated authorization headers and failure
boundaries rather than only the number of gateway calls.

The implementation was checked against the primary contracts for
[Java URI components](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html),
[Kotlin cancellable continuations](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/kotlinx-coroutines-core/common/src/CancellableContinuation.kt)
and [OkHttp cancellation/timeouts](https://github.com/square/okhttp/blob/parent-5.3.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt).
The timeout classification is tied to the pinned OkHttp behavior and should be
rechecked during dependency upgrades.

## Parity with iOS

Both SDKs support Domain inputs/outputs, endpoint Setup types, injected sessions,
response-to-Domain mapping, one unauthorized refresh attempt, fixture-backed real
providers and handwritten spies. Platform differences are intentional:

| Concern | Android | iOS |
| --- | --- | --- |
| Async transport | Kotlin `suspend`, OkHttp, `NetworkResponse` | Swift async, URLSession, Data/URLResponse |
| Request construction | `ApiRequestBuilder` supplied by SDK | Setup implements its own URLRequest builder |
| JSON decoding | Consumer chooses/configures codec | Consumer chooses/configures decoder |
| Fixture lookup | Test resolver, classpath name including `.json` | Test Setup conformance, filename without extension and owning bundle |
| Fixture request evaluation | Reads `setup.request.url` | Does not evaluate `setup.request` |
| I/O errors | Timeout mapping and `NetworkFailure(cause)` | Foundation transport errors propagate |
| Spy failures/assertions | `failingMethods`, `PlannedFailure`, runner-independent check | Legacy `failingMethos`, Swift Testing assertions |

The Android guide documents those actual interfaces. It does not introduce Swift
bundle rules, protocol conformance requirements or misspelled Swift members into
generated Kotlin code.

## Consumer guidance and portability

- README.md and README_AI.md route to one maintained integration guide.
- The `androidcleannetwork` skill contains that guide, metadata and a complete
  Gradle example, including its wrapper and resources.
- The sample separates a pure Kotlin Domain module from an Android Data module.
  It uses fictional User values, example.com URLs and a consumer-owned codec.
- The installer refuses existing destinations, including broken symlinks, and
  excludes build caches and machine-specific settings. Tests inspect a copied
  bundle's links, wrapper and resources.
- CI validates the SDK, executable example and installer. Consumer-project
  installation is explicit; a Gradle dependency does not automatically load AI
  instructions. Agent Skills help discovery but do not guarantee generated code.

## Validation

The baseline passed test and lint with nine distinct tests in each Android
variant (seven runtime, two testing helpers). The new regressions were then
reproduced before applying fixes. Final results on the audited source:

| Check | Result |
| --- | --- |
| Runtime SDK | 27 tests passed in debug and 27 in release |
| Testing helpers | 16 tests passed in debug and 16 in release |
| SDK packaging | Both release AARs and POMs generated in an isolated local Maven repository |
| User example | 15 tests, Domain build, Android lint and debug AAR passed |
| Example installed outside this checkout | The same 15 tests, build and lint passed using its own wrapper/resources |
| Independent agent integration | A separate Item GET/POST feature generated from the installed skill passed 18 tests, build and lint |
| Skill installer | Eight tests passed, including no-overwrite and copied-resource/reference checks |
| Skill metadata | Official skill validator passed |
| Independent review | No unresolved material findings after the final corrections |

All executed test suites reported zero failures, errors or skipped tests. The
SDK totals are **43 distinct tests**, executed in both variants; they are not 86
different scenarios. The independent Item integration additionally exercised
query parameters, list envelopes, required fields and nullable/absent strings.

Lint finished without errors or fatal issues. Its retained warnings concern
available dependency/plugin updates: 12 entries in the SDK report (four notices
repeated by the analysis), and one Kotlin test-dependency notice in each consumer
example. This audit preserved the established toolchain/dependency versions.
Local equivalents of the CI commands were executed; the updated GitHub Actions
workflow has not been run remotely as part of this local change.

Use JDK17, Android SDK platform35 and the repository's Gradle8.10.2 wrapper:

```sh
./gradlew clean test lint publishToMavenLocal --no-daemon
ANDROID_CLEAN_NETWORK_PATH="$PWD" ./gradlew \
  -p skills/androidcleannetwork/assets/UserExample \
  :user-domain:build :user-data:testDebugUnitTest \
  :user-data:lintDebug :user-data:assembleDebug --no-daemon
python3 -m unittest discover -s scripts/tests -v
```

Local Maven publication verifies AAR/POM packaging; it does not release artifacts
to an external repository. It can be isolated with the `maven.repo.local` JVM
system property, pointing to an absolute temporary directory.

## Retained limits

- Only GET, POST, PUT and PATCH are exposed. OkHttp rejects a body on GET and a
  null body on POST/PUT/PATCH; explicitly supply empty bytes when the API requires
  an empty body. This existing contract was not changed.
- Response bytes are buffered in memory. The reported URL remains the original
  request URL; redirects and connection retries follow the injected OkHttp client.
- The SDK does not supply a JSON codec, token store, OAuth flow, refresh
  deduplication, general backoff or synchronized consumer-spy state.
- The fixture session returns status200 and cannot substitute for HTTP status,
  cancellation or live-authentication tests.
- Validation covers JVM unit tests, Android builds and lint. It is not an emulator
  or device test, live API certification, or a dependency-vulnerability assessment.
