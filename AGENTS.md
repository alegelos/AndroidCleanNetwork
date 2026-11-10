# Working in AndroidCleanNetwork

This repository contains the Kotlin/Android networking SDK and its test utilities.
Consumer integration guidance lives in `skills/androidcleannetwork/references/integration.md`;
the complete fictional example is `skills/androidcleannetwork/assets/UserExample`.
Use `skills/androidcleannetwork/SKILL.md` when generating consumer integrations.

## Contracts

- Preserve public API compatibility unless the task explicitly changes it.
- Domain inputs and outputs stay independent of transport. New examples use
  `*Setup`, `*Provider`, request `*DTO`, response `*Response` and `.domain`.
- Use Kotlin/Android contracts from the installed source. The iOS SDK is a
  conceptual counterpart; its bundle/conformance and spy APIs differ.
- Keep examples small and fictional: User/Item names and `example.com` URLs.
  Do not introduce customer, employer, reference-project names or credentials.
- Maintain one technical guide inside the installable skill. README.md and
  README_AI.md introduce and route to it rather than duplicate specifications.
- Skill references, wrapper and sample resources must survive installation
  outside this repository. Adding the Gradle dependency does not install a skill.
- The current module version is 1.0.1. Check actual repository/publication state
  before describing that coordinate as a published release.

## Verification

Use JDK 17 and Android SDK platform 35. From the repository root:

```sh
./gradlew test lint assemble --no-daemon
ANDROID_CLEAN_NETWORK_PATH="$PWD" ./gradlew \
  -p skills/androidcleannetwork/assets/UserExample \
  :user-domain:build :user-data:testDebugUnitTest \
  :user-data:lintDebug :user-data:assembleDebug --no-daemon
python3 -m unittest discover -s scripts/tests
```

Select an installed JDK with `JAVA_HOME` and SDK with `ANDROID_HOME` per command
when needed; do not change the user's global toolchain settings. Tests use local
fixtures and MockWebServer, with no production access. Preserve cancellation
and response/resource cleanup when changing the transport.

For guidance/example changes, check API and version claims, Markdown links and a
standalone installation. For substantive skill changes, exercise a realistic
integration in a temporary consumer project; schema validation alone does not
prove useful agent behavior. Keep generated caches, local paths and test reports
out of published examples. Inspect publishing tasks before running them; the CI
workflow's local Maven verification is not an external release.
