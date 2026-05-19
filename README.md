# Play 1 Toolkit

> Modern IntelliJ IDEA support for legacy Play Framework 1.x applications.

## Overview

Play 1 Toolkit is an IntelliJ IDEA Ultimate plugin that dramatically improves the developer experience when working on legacy Java applications built with Play Framework 1.x.

**The problem:** Opening an old Play 1 project in IntelliJ shows red imports (`import play.mvc.*;`), no run configuration, and no source root configuration.

**The solution:** Play 1 Toolkit detects the project, attaches the Play framework libraries, configures source roots, and creates a run configuration — all in one action.

## Features

- **Auto-detection**: Automatically detects Play 1 projects on open
- **Repair Project Setup**: One-click action to fix all configuration issues
  - Attaches Play framework JARs and dependencies
  - Configures `app/` as source root, `test/` as test root, `conf/` as resources
  - Creates a Play 1 run/debug configuration
- **Play Home Settings**: Configure your Play 1 installation with auto-detect
- **Run/Debug**: Launch Play 1 apps directly from IntelliJ with full debug support
- **`conf/routes` support**: Syntax highlighting, Ctrl+Click navigation to controllers, completion, and error annotations
- **Tool Window**: Project overview with status, routes list, and diagnostics panel
- **`render()` navigation**: Ctrl+Click on `render()` in a controller opens the implicit view (`app/views/Controller/action.html`)
- **Gutter icons**: Bidirectional navigation between controller action methods and their routes in `conf/routes`
- **Missing view inspection**: Warning on `render()` calls where the view file doesn't exist, with quick fix to create it

## Requirements

- IntelliJ IDEA Ultimate 2024.1+
- Play Framework 1.x installation (Play Home)
- Java 17+ for the IDE

## Development

### Prerequisites

- JDK 17+
- Gradle 8.x (included via wrapper)

### Build

```bash
# Build plugin
./gradlew buildPlugin

# Run in IntelliJ sandbox
./gradlew runIde

# Run unit tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin
```

### Testing the plugin in a live sandbox IDE

The fastest way to test the plugin for real is `./gradlew runIde`. This launches a sandboxed IntelliJ IDEA instance with the plugin already installed. **The first run downloads the full IDE (~1 GB) — be patient.**

#### Step 1 — Launch the sandbox

```bash
./gradlew runIde
```

A separate IntelliJ IDEA window opens. Your normal IDE is unaffected.

#### Step 2 — Open a Play 1 project

You have two options:

**Option A — Use the sample app included in this repo** (easiest, no Play Home needed for basic features):

In the sandbox IDE: `File > Open` → navigate to `<this repo>/sample-play1-app/` → OK.

**Option B — Use a real Play 1 project** (needed to test repair, run config, and features that require Play JARs on the classpath):

Open any Play 1 project. You need a real Play 1 installation for this (see Step 3).

#### Step 3 — Configure Play Home (needed for most features)

Features that resolve Java PSI (render navigation, gutter icons, missing view inspection) require Play JARs to be on the classpath. Without them, `play.mvc.Controller` is unresolvable and these features silently do nothing.

1. `Settings > Tools > Play 1 Toolkit`
2. Set **Play Home** to your Play 1 installation directory (e.g. `/opt/play-1.2.7`)
3. Click **Auto-detect** if you have `PLAY_HOME` set or Play installed in a standard path
4. The field should show `Play 1.2.7 — OK` (or similar)

> If you don't have Play 1 installed: `cd /tmp && wget https://...` or use the samples from `/tmp/play1-master/` if available locally. Note that `/tmp/play1-master` contains sources, not compiled JARs — you need a binary distribution.

#### Step 4 — Repair the project

`Tools > Play 1 > Repair Project Setup`

This attaches the Play JARs to the project. After this, `import play.mvc.*;` resolves (no more red imports).

#### What to test and how

| Feature | How to trigger | Expected result |
|---------|---------------|-----------------|
| **Auto-detection** | Open a Play 1 project | Notification balloon "Play 1 project detected" |
| **Repair** | `Tools > Play 1 > Repair Project Setup` | Dialog showing OK for library, source roots, run config |
| **Syntax highlighting** | Open `conf/routes` | HTTP methods in blue, paths in white, controllers in green, actions in purple |
| **Ctrl+Click in routes** | Ctrl+Click on `Application.index` in routes | Jumps to `Application.java`, `index()` method |
| **render() navigation** | Open a controller, Ctrl+Click on `render()` | Navigation popup includes "Go to Play 1 View" → opens the `.html` file |
| **Gutter icons (Java)** | Open a controller | Small `→` icon in the gutter next to `public static void index()` — click to jump to the matching route |
| **Gutter icons (routes)** | Open `conf/routes` | Small `←` icon next to `Application.index` — click to jump to the Java method |
| **Missing view inspection** | In a controller, call `render()` from a method that has no `app/views/X/Y.html` | Yellow underline warning; hover shows "View not found: ..."; Alt+Enter → "Create view" |
| **Tool Window** | Click "Play 1" in the right sidebar | Tabs: Status (Play detected, Play Home), Routes (tree), Diagnostics (unresolved controllers) |
| **Run/Debug** | Run ▶ "Play 1 App" | App starts on http://localhost:9000 |

#### Troubleshooting

**render() navigation / gutter icons don't work**
→ Play JARs are not on the classpath. Run "Repair Project Setup" first so that `play.mvc.Controller` is resolvable.

**"Play 1 project detected" notification doesn't appear**
→ The project must have at least 2 of: `conf/application.conf`, `conf/routes`, `app/controllers/`. Check the project structure.

**Sandbox is slow or hangs on first run**
→ Normal — it's downloading and extracting the IDE. Wait for the Gradle task to complete.

### Project Structure

```
src/
├── main/kotlin/com/github/pablolec/play1toolkit/
│   ├── actions/          User actions (Repair, etc.)
│   ├── config/           Plugin settings (Play Home, etc.)
│   ├── detection/        Play 1 project and home detection (no IDE deps)
│   ├── model/            Internal data model
│   ├── project/          Library, source root, run config managers
│   ├── run/              Run/Debug configuration type
│   ├── routes/           conf/routes file type support
│   ├── services/         Project-level services (index, state)
│   └── toolwindow/       Play 1 Tool Window
└── test/kotlin/          Tests (unit + platform)
sample-play1-app/         Sample Play 1 project for testing
docs/                     Work documentation and ADRs
```

### Documentation

- [`docs/todo.md`](docs/todo.md) — Current task list
- [`docs/runlog.md`](docs/runlog.md) — Execution journal
- [`docs/work-breakdown.md`](docs/work-breakdown.md) — Work breakdown by lot
- [`docs/architecture.md`](docs/architecture.md) — Architecture decisions
- [`docs/adr/`](docs/adr/) — Architecture Decision Records

## Contributing

This plugin is in active development. See `docs/work-breakdown.md` for the current roadmap and `docs/todo.md` for the task list.

## License

Apache 2.0
