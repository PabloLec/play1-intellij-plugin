# Play v1 Toolkit

> Modern IntelliJ IDEA support for legacy Play Framework 1.x applications.

## Overview

Play v1 Toolkit is an IntelliJ IDEA plugin that provides a complete IDE experience for Java applications built with Play Framework 1.x — a framework that predates modern build tools and has no official IntelliJ support.

**The problem:** Opening a Play 1 project in IntelliJ gives you red imports (`import play.mvc.*;`), no run configuration, unrecognised configuration files, and no navigation between routes, controllers, views, or message keys.

**The solution:** Play v1 Toolkit detects Play 1 projects automatically, repairs the project setup, and adds deep language intelligence for every Play 1 file type.

---

## Compatibility

| Dimension | Value |
|---|---|
| Plugin version | 0.1.0-SNAPSHOT |
| IntelliJ IDEA | 2024.1 – 2026.3 (build 241–263) |
| IDE edition | Ultimate (Java plugin required) |
| Play Framework | 1.x (any minor version) |
| JDK (for the IDE) | 17+ |

---

## Features

### Project Setup

**Auto-detection**
On project open, the plugin heuristically detects Play 1 projects by looking for `conf/application.conf`, `conf/routes`, and `app/controllers/`. When detected, a notification balloon offers to repair the setup.

**Repair Project Setup** (`Tools > Play v1 Toolkit > Repair Project Setup`)
One-click repair that:
- Attaches Play framework JARs and all dependencies from `lib/` and `lib/managed/`
- Marks `app/` as source root, `test/` as test root, `conf/` as resources root
- Creates a Play 1 run/debug configuration

**Sync Dependencies** (`Tools > Play v1 Toolkit > Sync Dependencies`)
Runs `play deps` to download project dependencies and attaches the resulting JARs.

**Play Home Settings** (`Settings > Tools > Play v1 Toolkit`)
Configure the path to your Play 1 installation with auto-detection from `PLAY_HOME` or common installation paths.

**Library watcher**
Monitors `lib/` for JAR additions/removals and updates the project classpath automatically.

---

### Run / Debug

A dedicated Play 1 run configuration type launches `play run` (or `play debug`) through IntelliJ's run/debug infrastructure. Full debugger support including breakpoints and variable inspection.

---

### `conf/routes` — Custom Language

Full custom language support for Play 1 route files.

| Capability | Details |
|---|---|
| Syntax highlighting | HTTP method, URL path, controller class, action method — each in distinct colour |
| Ctrl+Click navigation | `Application.index` in routes → jumps to `Application.java`, `index()` method |
| Reverse navigation | Ctrl+Click a controller method → navigates to the matching route entry |
| Completion | Controller class names and action method names with live filtering |
| Annotator | Highlights unresolved controllers and actions as errors |
| Gutter icons (routes) | `←` icon on each route line → navigate to the Java action method |
| Gutter icons (Java) | `→` icon on each public static controller method → navigate to the route |
| Find Usages integration | Controller classes and action methods show usages from `conf/routes` |
| Implicit usage suppression | Public static controller actions are not flagged as "unused" by IntelliJ |
| Response inlay hints | Each route shows an inline annotation of the action's response type (render, redirect, JSON, status, mixed…) |

---

### `render()` — View Navigation

**Goto Declaration from `render()`**
Ctrl+Click on a `render()` call in a controller opens the corresponding HTML view file (`app/views/Controller/action.html`). Works for the implicit view (no-argument `render()`) and explicit view names.

**Missing view inspection** (JAVA, WARNING)
Flags `render()` calls whose target view file does not exist. Quick fix `Create view` generates the file at the expected path.

---

### `conf/application.conf` — Configuration Intelligence

Full custom language support for Play 1 application configuration files.

| Capability | Details |
|---|---|
| Syntax highlighting | Keys, values, comments, profile prefixes, environment variable references |
| Ctrl+Click navigation | `Play.configuration.get("key")` in Java → jumps to the key definition in `application.conf` |
| Reverse navigation | Key in `application.conf` → find all Java usages |
| Completion (Java) | String literals in configuration access calls propose all known keys |
| Completion (conf) | Key name completion against known Play framework keys |
| Rename refactoring | Rename a key in `application.conf` → propagates to all Java references |
| Inlay hints (Java) | `Play.configuration.get("key") /* = value */` shown inline |
| Inlay hints (conf) | Profile override keys show the effective resolved value inline |
| Quick documentation | Hover a key in Java or conf → shows value, profile variants, usages count |
| Gutter icons | Keys with profile overrides show navigation to all variants |
| Settings | Per-project active profile selection (`Settings > Play v1 Toolkit > Configuration Intelligence`) |

**Inspections for `application.conf`:**

| Inspection | Level | Description |
|---|---|---|
| Unresolved Play config key (Java) | WEAK WARNING | `configuration.get("missing.key")` — key not declared in conf |
| Unused Play config key | INFORMATION | Key declared in conf but never read from Java |
| Duplicate Play config key | WARNING | Same key defined twice in the same file |
| Profile override without default | INFORMATION | `%prod.key=value` with no `key=value` default |
| Unresolved environment variable | WEAK WARNING | `${ENV_VAR}` reference that isn't set in the environment |
| Suspicious profile prefix | WEAK WARNING | Profile prefix that doesn't match any known environment |
| Unknown Play framework key | WEAK WARNING | Key not in the built-in list of Play 1 framework keys |

---

### `conf/messages` — i18n Intelligence

Full custom language support for Play 1 internationalisation files (`conf/messages` for the default locale, `conf/messages.fr`, `conf/messages.en-US`, etc.).

| Capability | Details |
|---|---|
| Syntax highlighting | Keys in bold, values as strings, `%s`/`%d` placeholders highlighted, `#` comments in italics |
| Ctrl+Click from Java | `Messages.get("key")` → jumps to the key declaration in `conf/messages` |
| Ctrl+Click from HTML | `&{'key'}` in an `app/views/` template → jumps to the key declaration |
| Find Usages (messages → Java) | Key in conf/messages → lists all `Messages.get("key")` call sites |
| Find Usages (messages → HTML) | Key in conf/messages → lists all `&{'key'}` usages in view templates |
| Completion (Java) | String literals in `Messages.get(…)` propose all known message keys with their default value as tail text |
| Completion (HTML) | `&{'<caret>'}` in view templates proposes all known message keys |
| Rename refactoring | Renaming a key in any locale file automatically renames it in all other locale files and all Java/HTML usages |
| Inlay hints (Java) | `Messages.get("key") /* = Hello World */` shown inline; `[N locales]` appended when multiple translations exist |
| Inlay hints (HTML) | `&{'key'} /* = Hello World */` shown inline in view templates |
| Quick documentation | Hover a key → shows value for each locale, plus usage count |
| Gutter icons | Keys with locale variants show a globe icon navigating to all translations; keys with Java/HTML usages show a usages icon |

**Inspections for messages:**

| Inspection | Level | Description |
|---|---|---|
| Unknown message key (Java) | WEAK WARNING | `Messages.get("missing")` — key not declared in `conf/messages` |
| Unknown message key (HTML) | WEAK WARNING | `&{'missing'}` — key not declared in `conf/messages` |
| Duplicate message key | WARNING | Same key defined twice in the same messages file |
| Missing locale translation | INFORMATION (off by default) | Key present in default `conf/messages` but absent from a locale file |
| Placeholder count mismatch | WEAK WARNING | `Messages.get("fmt", arg)` where `fmt=Hello %s %s` has 2 placeholders but only 1 argument passed |

---

### Tool Window

A dedicated **Play v1 Toolkit** panel in the right sidebar with three tabs:

- **Status** — Play detection result, configured Play Home, library attachment state
- **Routes** — Tree view of all routes grouped by controller
- **Diagnostics** — Unresolved controllers and actions in `conf/routes`

---

## Development

### Prerequisites

- JDK 17+
- Gradle (wrapper included — `./gradlew`)

### Build commands

```bash
# Build the plugin ZIP
./gradlew buildPlugin

# Launch a sandboxed IntelliJ instance with the plugin installed
./gradlew runIde

# Run unit tests
./gradlew test

# Verify plugin descriptor and API compatibility
./gradlew verifyPlugin
```

The first `runIde` downloads the full IDE sandbox (~1 GB). Subsequent runs are fast.

### Testing manually

Open `<repo>/sample-play1-app/` in the sandbox IDE for quick smoke testing of most features without needing a real Play installation. For repair, run config, and classpath-dependent features (render navigation, gutter icons) you need a real Play 1 binary distribution and must configure **Play Home** in settings.

**Features that require Play Home configured and Repair run:**
- `render()` navigation and missing view inspection (need `play.mvc.Controller` on the classpath)
- Gutter icons on Java controller methods
- Run/Debug configuration

**Features that work without Play Home:**
- `conf/routes`, `conf/application.conf`, `conf/messages` — all language intelligence
- Ctrl+Click navigation in all directions
- Completions, inlay hints, inspections, rename

### Troubleshooting the sandbox

| Symptom | Cause / Fix |
|---|---|
| render() navigation doesn't work | Play JARs not on classpath — run Repair Project Setup |
| "Play 1 project detected" doesn't appear | Need at least 2 of: `conf/application.conf`, `conf/routes`, `app/controllers/` |
| Gutter icons missing on controller methods | Same as above — Repair needed so `play.mvc.Controller` resolves |
| Sandbox hangs on first launch | Downloading IDE — wait for Gradle task completion |

---

## Project Structure

```
src/main/kotlin/com/github/pablolec/play1toolkit/
├── actions/            User-triggered actions (Repair, Sync Deps)
├── config/             Application-level settings (Play Home)
├── detection/          Play project and Play Home detection (no IntelliJ PSI dependencies)
├── inspection/         Missing view inspection + quick fix
├── lineMarker/         Gutter icons for controller ↔ routes navigation + response markers
├── model/              Shared data models
├── project/            Library manager, source root manager, run config manager, lib watcher, CLI runner
├── render/             render() → view navigation
├── response/           Action response analysis (render / redirect / JSON / status classification)
├── routes/             conf/routes custom language
│   ├── psi/            PSI node types (RoutesFile, RoutesRouteElement)
│   ├── RoutesLexer     Hand-written state-machine lexer
│   ├── RoutesParser    Inline parser
│   ├── …Highlighter    Syntax highlighting
│   ├── …Annotator      Error annotations for unresolved targets
│   ├── …Contributor    PSI references (routes → Java)
│   ├── …Searchers      Find Usages (Java → routes)
│   └── …InlayHints     Route response type hints
├── run/                Play 1 run/debug configuration type
├── services/           Project-level services (project state, command execution)
├── toolwindow/         Play v1 Toolkit tool window (Status, Routes, Diagnostics panels)
├── playconfig/         conf/application.conf custom language
│   ├── lang/           Language, FileType, Lexer, Parser, Highlighter, TokenTypes, ElementTypes
│   ├── psi/            PlayConfigFile, PlayConfigProperty
│   ├── model/          PlayConfigEntry, resolution model
│   ├── service/        PlayConfigService (cached PSI index), PlayConfigKnownKeys
│   ├── settings/       Per-project active profile setting
│   ├── references/     Java → conf contributor, conf → Java usage searcher, rename processor
│   ├── completion/     Java + conf completion contributors
│   ├── hints/          Java + conf inlay hints providers
│   ├── lineMarker/     Gutter icons (profile variants)
│   ├── documentation/  Quick documentation provider
│   └── inspections/    7 inspections + quick fix
└── playmessages/       conf/messages custom language
    ├── lang/           Language, FileType, FileTypeDetector, Lexer, Parser, Highlighter, TokenTypes, ElementTypes
    ├── psi/            PlayMessagesFile, PlayMessagesProperty
    ├── model/          PlayMessageEntry
    ├── service/        PlayMessagesService (cached PSI index per file + locale)
    ├── references/     Java contributor + HTML contributor + goto handlers + usage searchers + rename processor
    ├── completion/     Java + HTML completion contributors
    ├── hints/          Java + HTML inlay hints providers
    ├── lineMarker/     Gutter icons (locale variants, usages)
    ├── documentation/  Quick documentation provider
    └── inspections/    5 inspections + quick fix

src/test/kotlin/        Unit tests (JUnit 4, no IntelliJ platform dependency)
sample-play1-app/       Minimal Play 1 project for sandbox testing
```

### Key architectural patterns

**Custom languages** — Routes, PlayConfig, and PlayMessages each follow the same pattern: hand-written state-machine lexer → inline recursive parser → `PsiFileBase` + `ASTWrapperPsiElement` PSI nodes → service for indexed data → reference contributors for navigation. No dependency on the IntelliJ Properties or XML language plugins.

**Service + cache** — `PlayConfigService` and `PlayMessagesService` index their files using `CachedValuesManager.getCachedValue(file)` per file, with `PsiModificationTracker.MODIFICATION_COUNT` as the invalidation key. This keeps lookups fast and automatically invalidates on any PSI edit.

**Navigation from Java** — Reference contributors register `PsiLiteralExpression` patterns. Context detectors (`PlayConfigContextDetector`, `PlayMessagesContextDetector`) verify the call site (method name, qualifier class, argument index) before creating references. This avoids polluting every string literal in the project.

**Navigation from HTML** — HTML view templates use `GotoDeclarationHandler` (not reference contributors) because IntelliJ's HTML parser creates intermediate `XmlEntityRef` nodes for `&{` syntax, making composite-element reference contributors unreliable for Ctrl+Click. The handler scans raw file text + offset comparison.

**Rename cross-locale** — `PlayMessagesRenameProcessor` automatically adds all other locale variants of the same key into `allRenames` during `prepareRenaming()`, so a single rename propagates across all `conf/messages.*` files and all Java/HTML usages simultaneously.

**Response analysis** — `PlayActionResponseAnalyzer` walks the PSI method body recursively to classify the action's response type (`RENDER`, `REDIRECT`, `JSON`, `STATUS`, `MIXED`, `UNKNOWN`). Results are cached per method via `PlayActionResponseService` and displayed as inlay hints in `conf/routes`.

---

## License

Apache 2.0
