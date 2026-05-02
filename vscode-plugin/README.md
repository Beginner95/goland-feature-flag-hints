# Feature Flag Hints for VSCode

A VSCode extension that shows the **production state of [Flipt](https://flipt.io/) feature flags inline in Go code**.

```go
if !i.ff.IsEnabled(ctx, tmpFFDisconnectedConsumer) { //  false
```

```go
if p.feature.IsEnabled(ctx, "ff_tmp_outbox_event_task_created_topic") { //  true
```

---

## How it works

1. **`flipt-sync`** (same as GoLand plugin) - a CLI tool that fetches all feature flags from Flipt REST API and writes them to `feature-flags.json` in your project root.
2. **VSCode extension** - reads `feature-flags.json` and adds inlay hints next to every `IsEnabled()` call in your Go code. Supports both string literals and named constants as flag arguments.

---

## Requirements

- VSCode **1.74+**
- Go **1.21+** (for flipt-sync CLI)

---

## Part 1 - flipt-sync CLI (same as GoLand plugin)

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

```makefile
fetch-flags:
	FLIPT_URL=https://flipt.example.com FLIPT_NAMESPACE=default flipt-sync
```

### Gitignore

```gitignore
feature-flags.json
```

---

## Part 2 - VSCode Extension

### Install from VSIX (development)

1. Navigate to the `vscode-plugin` directory:
   ```bash
   cd vscode-plugin
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Build the extension:
   ```bash
   npm run compile
   ```

4. Package the extension:
   ```bash
   npm install -g @vscode/vsce
   vsce package
   ```

5. Install the `.vsix` file in VSCode:
   - **Extensions** → **⋮ (three dots)** → **Install from VSIX** → select `feature-flag-hints-1.0.0.vsix`

### Build from source

```bash
cd vscode-plugin

# Install dependencies
npm install

# Build
npm run compile

# Package
npm install -g @vscode/vsce
vsce package
```

### Enable inlay hints

After installing the extension, make sure inlay hints are enabled in VSCode:

**Settings → Text Editor → Inlay Hints** → enable **Enabled**

You can also configure:

- **Feature Flag Hints: Enable** - toggle the feature
- **Feature Flag Hints: File Path** - path to `feature-flags.json` (default: `feature-flags.json`)

---

## Usage workflow

```bash
# 1. Fetch current prod state of all flags
FLIPT_URL=https://flipt.example.com flipt-sync

# 2. Open your project in VSCode
# → inlay hints appear automatically next to every IsEnabled() call
```

The extension automatically reloads `feature-flags.json` whenever the file changes.

---

## Supported call patterns

The extension resolves both string literals and named Go constants:

```go
const myFlag = "my_feature_flag"

// string literal
ff.IsEnabled(ctx, "my_feature_flag") //  true

// named constant
ff.IsEnabled(ctx, myFlag)            //  true
```

---

## Project structure

```
goland-feature-flag-hints/
├── flipt-sync/          # Go CLI (shared)
│   └── main.go
├── plugin/              # GoLand plugin (Kotlin/Gradle)
│   └── src/main/kotlin/...
└── vscode-plugin/       # VSCode extension (TypeScript)
    ├── package.json
    ├── tsconfig.json
    └── src/
        └── extension.ts
```

---

## License

MIT
