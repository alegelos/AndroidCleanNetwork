# Changelog

## 1.0.1 — 2026-11-10

- Preserve percent-encoded base URL paths when constructing requests.
- Cancel the underlying OkHttp call when its coroutine is cancelled, including
  while waiting for headers or reading the response body.
- Map socket and total-call timeouts to `ApiErrors.TimeOut` while preserving
  other I/O failures and their causes.
- Expand regression coverage for transport, requests, unauthorized retry,
  fixtures and provider-spy helpers. Preserve public API and dependency versions.
- Add the portable `androidcleannetwork` Agent Skill, a canonical integration
  guide, a fictional User example with an independent Domain module and tests,
  a safe installer, and instructions for Codex, Claude Code and GitHub Copilot.
- Extend CI to verify the SDK, consumer example and skill installer. Record the
  audit and platform differences in [docs/AUDIT.md](docs/AUDIT.md).
- Configure CI's Android setup explicitly, avoiding the unavailable legacy
  `tools` package requested by the setup action's default configuration.

The GitHub release includes a skill ZIP, a Maven-layout ZIP containing both
release AARs, sources, POMs and Gradle metadata, and SHA-256 checksums. The Maven
ZIP is a downloadable file repository; this release does not publish to Maven
Central. See [README.md](README.md) for SDK installation and
[README_AI.md](README_AI.md) for skill installation.
