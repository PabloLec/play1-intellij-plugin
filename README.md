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
- **conf/routes support**: Syntax highlighting and navigation *(coming soon)*
- **Tool Window**: Project overview with status, routes, and diagnostics *(coming soon)*

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
