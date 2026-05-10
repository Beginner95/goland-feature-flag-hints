# GoLand Feature Flag Hints

A GoLand plugin that shows the **production state of [Flipt](https://flipt.io/) feature flags inline in Go code**, and a CLI tool to sync flag states from Flipt.

Works with any method name and any SDK wrapper - detection is based on flag key values, not method names.

```go
if !i.ff.IsEnabled(ctx, tmpFFDisconnectedConsumer) {        // false
if p.feature.IsEnabled(ctx, "ff_tmp_outbox_event") {        // true
voxID := c.feature.GetVoxCampaignID(ctx, entity.FFVoxEnabled, ...) // true
```

---

## How it works

1. **`flipt-sync`** - a CLI tool that fetches all feature flags from Flipt REST API and writes them to `feature-flags.json` in your project root.
2. **GoLand plugin** - reads `feature-flags.json` and adds inlay hints next to every string or constant in Go code whose value matches a flag key. Supports string literals, local constants, and package-level constants.

---

## Requirements

- GoLand **2023.3** or newer
- Go **1.21+** (for flipt-sync)
- Java **17+** and Gradle **8.5+** (to build the plugin from source)

---

## Part 1 - flipt-sync CLI

### Install

```bash
go install github.com/Beginner95/goland-feature-flag-hints/flipt-sync@latest
```

### Usage

Run from your **project root** (the directory where `feature-flags.json` should be created):

```bash
FLIPT_URL=https://flipt.example.com flipt-sync
```

The file `feature-flags.json` will be written to the current directory.

### Environment variables

| Variable          | Required | Default              | Description                          |
|-------------------|----------|----------------------|--------------------------------------|
| `FLIPT_URL`       | yes      | -                    | Base URL of your Flipt instance      |
| `FLIPT_NAMESPACE` | no       | `default`            | Flipt namespace                      |
| `FLIPT_TOKEN`     | no       | -                    | Bearer token (if auth is enabled)    |
| `OUTPUT_PATH`     | no       | `feature-flags.json` | Output file path                     |

### Makefile integration (optional)

Add to your project's `Makefile` so you don't have to type env vars every time:

```makefile
fetch-flags:
    FLIPT_URL=https://flipt.example.com FLIPT_NAMESPACE=default flipt-sync
```

### Gitignore

Add `feature-flags.json` to your project's `.gitignore` - it contains prod state and changes frequently:

```gitignore
feature-flags.json
```

---

## Part 2 - GoLand Plugin

### Install from release (recommended)

1. Download the latest `feature-flag-hints-*.zip` from the [Releases](https://github.com/Beginner95/goland-feature-flag-hints/releases) page.
2. In GoLand: **Settings → Plugins → ⚙️ → Install Plugin from Disk** → select the zip.
3. Restart GoLand.

### Build from source

```bash
# Install Java 17
sudo apt install openjdk-17-jdk   # Ubuntu/Debian
brew install openjdk@17            # macOS

# Install Gradle (via SDKMAN - recommended)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.5

cd plugin
gradle wrapper
./gradlew buildPlugin
# → build/distributions/feature-flag-hints-*.zip
```

Then install the zip via **Settings → Plugins → Install Plugin from Disk**.

### Enable inlay hints

After installing the plugin, make sure inlay hints are enabled in GoLand:

**Settings → Editor → Inlay Hints → Go** → enable **Feature Flag (prod state)**

---

## Usage workflow

```bash
# 1. Fetch current prod state of all flags
FLIPT_URL=https://flipt.example.com flipt-sync

# 2. Open your project in GoLand
# → inlay hints appear automatically next to every flag key in code
```

The plugin reloads `feature-flags.json` automatically whenever the file changes - no IDE restart needed.

---

## Supported call patterns

Detection is key-based: the plugin shows a hint wherever a string value matches a key in `feature-flags.json`, regardless of method name or SDK.

```go
// String literal - any method, any SDK
ff.IsEnabled(ctx, "my_feature_flag")          // true
client.Evaluate("my_feature_flag", entityID)  // true
ff.Check(ctx, "my_feature_flag")              // true

// Local named constant
const myFlag = "my_feature_flag"              // true  ← hint at definition too
ff.IsEnabled(ctx, myFlag)                     // true

// Package-level constant
// entity/feature_flags.go: const FFVoxEnabled = "vox_enabled"
ff.GetVoxCampaignID(ctx, entity.FFVoxEnabled, ...) // true
```

---

## Project structure

```
goland-feature-flag-hints/
├── flipt-sync/          # Go CLI (go install .../flipt-sync@latest)
│   └── main.go
├── plugin/              # GoLand plugin (Kotlin/Gradle)
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── src/main/kotlin/ru/webvaha/ffplugin/
│       ├── FeatureFlagService.kt             # reads & caches feature-flags.json
│       └── FeatureFlagInlayHintsProvider.kt  # inlay hints in Go editor
├── CHANGELOG.md
└── go.mod
```

---

## License

MIT
