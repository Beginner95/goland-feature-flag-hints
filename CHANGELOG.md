# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-05-10

### Added
- Universal feature flag detection: hints now appear on any string literal or constant
  whose value matches a key in `feature-flags.json`, regardless of the method name or SDK used.
- Support for package-level constants passed as flag arguments (`entity.FFVoxEnabled`,
  `flipt.SomeFlag`, etc.) - qualified references are now resolved to their string value.

### Changed
- Detection logic no longer relies on a hardcoded method name (`IsEnabled`).
  Any call site, any wrapper, any SDK is supported as long as the flag key is present in code.

### Performance
- `getFlags()` is now called once per file scan (hoisted to `getCollectorFor`) instead of
  once per PSI element, reducing redundant service lookups.
- Added pre-filter by minimum flag key length: `resolve()` is skipped for identifiers
  shorter than the shortest key in `feature-flags.json`, eliminating most unnecessary
  PSI resolution calls.

## [1.0.0] - 2026-05-09

### Added
- Initial release.
- Inlay hints showing production state (`true` / `false`) for Flipt feature flags.
- Detection of `IsEnabled(ctx, flagName)` calls in Go code.
- Support for string literals and named constants as the flag argument.
- `FeatureFlagService`: reads and caches `feature-flags.json` from the project root,
  invalidates cache on file modification.
- `flipt-sync`: CLI tool to pull flag states from the Flipt API into `feature-flags.json`.
