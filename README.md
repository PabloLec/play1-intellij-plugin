# Play v1 Toolkit

Play v1 Toolkit is an IntelliJ IDEA plugin for teams still maintaining **Play Framework 1.x** applications.

At a practical level, it does two things.

First, it helps IntelliJ treat an old Play 1 project like a real project again: libraries, source roots, run configuration, dependency sync, and basic project repair. Second, it adds IDE support for the parts of Play 1 that IntelliJ does not understand on its own: routes, templates, `application.conf`, message bundles, JPA models, jobs, and cache usage.

If you work on a legacy Play 1 codebase, that is the whole point of the plugin: less manual setup, less guesswork, and much faster navigation through code that would otherwise feel half-broken in the IDE.

## What Problem It Solves

Opening a Play 1 project in IntelliJ usually gives you a familiar mess:

- `play.*` imports do not resolve
- `app/`, `test/`, and `conf/` are not configured the way Play expects
- there is no dedicated run/debug setup
- `conf/routes`, `conf/application.conf`, and `conf/messages*` are treated like plain text
- navigation between routes, controllers, views, and templates is mostly gone
- Play 1-specific concepts such as `render()`, jobs, YAML fixtures, and cache tags are invisible to the IDE

Play v1 Toolkit fills that gap.

## Compatibility

| Item | Value |
| --- | --- |
| Plugin version | `0.1.0-SNAPSHOT` |
| IntelliJ IDEA | builds `261+` |
| IDE edition | Ultimate |
| Required bundled plugins | Java, YAML |
| JDK for the IDE | 21+ |
| Plugin build toolchain | Java/Kotlin 21 |
| Target applications | Play Framework 1.x |

The current build targets `IU 2026.1.2` through the Gradle IntelliJ Platform Plugin.

## Core Features

### Project Detection and Repair

The plugin detects Play 1 projects heuristically when a project is opened. It looks for the usual markers of a Play 1 layout and, when it finds them, it can guide or automate the initial setup.

The **Repair Project Setup** action handles the tedious part:

- validate the configured Play installation
- run `play deps` when it makes sense
- attach Play framework jars
- attach project jars from `lib/`
- supplement the classpath from `conf/dependencies.yml` using local Maven and Ivy caches
- mark `app/` as a source root
- mark `test/` as a test root
- mark `conf/` as a resources root
- create a dedicated Play run configuration
- assign a Java SDK if the project does not already have one

The plugin can also repair the project silently on open when Play has already been configured and the managed libraries are still missing.

There is also a watcher on `lib/`, so when new jars appear after a dependency sync, the classpath can be refreshed without making you repeat the whole setup by hand.

### Play CLI Integration

The tool window exposes a small set of Play commands that matter in day-to-day maintenance work:

- `clean`
- `test`
- `auto-test`
- `precompile`
- `war`
- `deps`

That sounds simple, but old Play installations are not always simple to execute anymore. The plugin handles a few ugly cases for you:

- native `play` launchers
- Python-based `play` launchers
- Windows `play.bat` launchers
- Play versions that expect either Python 2 or Python 3
- local Python discovery through `py`, `python3`, `python2`, and `python`
- fallback to managed **PyPy 2.7** or **PyPy 3.11** runtimes when no compatible local interpreter is available
- fallback to a separate **Play 1.2+ home just for `play deps`** when the target project runs on Play 1.1.x
- download of a recommended Play distribution for dependency resolution

### Run and Debug

The plugin contributes a dedicated IntelliJ run configuration type for Play 1 applications.

That configuration lets you control:

- application path
- active `play.id`
- HTTP port
- debug port
- JVM options
- environment variables

Internally, the launch path is built around `play.server.Server`, with the Play and project classpath reconstructed in the order the runtime expects.

## Language and Code Intelligence

### `conf/routes`

`conf/routes` is not treated as plain text. The plugin defines a proper IntelliJ language for it, with its own lexer, parser, PSI model, and IDE integrations.

What you get in practice:

- syntax highlighting
- route PSI parsing
- navigation from routes to controllers and actions
- reverse navigation from controllers and actions back to matching routes
- completion for controller names and action names
- annotations for unresolved controllers and actions
- gutter icons on route entries
- gutter icons on Java controllers and actions
- Find Usages support from Java into `conf/routes`
- suppression of false "unused" warnings on routed controller actions
- inlay hints that show the likely response type of each action

The route model also distinguishes normal dynamic routes from `staticDir` and `module` entries.

### Action Response Analysis

The plugin performs static analysis on Play controller actions and tries to classify the dominant response shape:

- HTML render
- redirect
- JSON
- text
- XML
- binary
- HTTP status
- mixed outcomes

This is used in route inlay hints, route views inside the tool window, and quick documentation. The analysis does not stop at the immediate method body either; it can follow some helper methods so the result stays useful on real code, not only on toy examples.

### `render()` and View Navigation

The Java side of Play view rendering is understood well enough to make navigation usable again.

Supported behavior includes:

- `Ctrl+Click` from `render()` to the implicit view path
- `Ctrl+Click` from `renderTemplate("...")` to the explicit template
- an inspection for missing Play views
- quick fixes to create the missing view
- template skeleton generation in the creation flow

### Play Templates

Template support covers `app/views/**`, including custom tags under `app/views/tags`.

#### Navigation and References

- navigation to templates referenced by `#{include ...}` and `#{extends ...}`
- navigation to reverse routes used inside templates
- navigation to Play static assets
- Java-to-template and template-to-Java navigation depending on context
- usages for templates and routes referenced from views

#### Completion

- built-in tag names
- custom tags
- template paths
- reverse routes
- assets under `public/`
- template variables
- tag parameters in supported contexts

#### Variable Resolution

This part goes further than simple text matching. The plugin resolves template variables from several places:

- Play implicit variables
- values passed to `render()`
- `renderArgs.put(...)`
- helper method propagation
- some include and extends flows
- variables injected by the cache support
- JPA-derived type information when a template variable maps back to a model

#### Template Inspections

Registered inspections include:

- missing template file
- unknown template tag
- unknown reverse route
- missing static asset
- unknown template variable
- unbalanced Play tag
- unrouted action
- suspicious reverse route argument count

#### Quick Documentation

The template documentation provider covers the things you usually need to inspect quickly:

- templates
- custom tags
- reverse routes
- assets
- variables
- built-in tags

### `conf/application.conf`

The plugin implements a dedicated language for `application.conf`, with PSI parsing and project-level indexing of configuration keys.

Supported behavior includes:

- syntax highlighting
- PSI parsing
- key indexing
- support for profile-prefixed keys such as `%dev` and `%prod`
- effective value resolution
- environment variable resolution
- Java completion for config access calls
- config-side completion for known Play keys
- Java-to-conf references
- conf-to-Java usages
- key rename support
- Java inlay hints
- conf inlay hints
- quick documentation
- gutter markers for profile variants

Environment resolution is done in a sane order:

- environment variables from the selected Play run configuration
- then system environment variables

#### Configuration Intelligence Settings

The plugin adds a project-level **Configuration Intelligence** page where you can:

- choose the active framework id
- register custom wrapper methods for config access
- add extra prefixes

#### `application.conf` Inspections

- config key used in Java but missing from the file
- config key declared but never used
- duplicate key
- profile override without a default value
- unresolved environment variable
- suspicious profile prefix
- unknown Play framework key

### `conf/messages` and Locale Files

The plugin supports the usual Play message files:

- `conf/messages`
- `conf/messages.fr`
- `conf/messages.en-US`
- other locale variants following the same pattern

It provides:

- syntax highlighting
- PSI parsing
- key and locale indexing
- Java references through `Messages.get(...)`
- template references through `&{'key'}`
- Java and template usage search
- Java completion
- template completion
- rename across locale variants and usages
- inlay hints
- quick documentation
- gutter icons for variants and usages

#### Message Inspections

- unknown message key in Java
- unknown message key in templates
- duplicate message key
- missing locale translation
- placeholder count mismatch

### Play JPA Models and YAML Fixtures

The plugin indexes Play/JPA models under `app/models/`.

That support includes:

- detection of classes that correctly extend `play.db.jpa.Model`
- extraction of fields, id field, and relations
- finder-string completion in Java
- references from finder strings to model fields
- model and field usages
- rename support for models and fields
- support for YAML fixtures
- YAML completion for models, fields, and relation targets
- YAML inspections for unknown models, fields, relation targets, and duplicate aliases
- quick documentation for models and relations
- inlay hints for supported finder patterns
- a **Models** panel in the tool window

There is also a broader model-classification service for `app/models/`, which helps separate actual entities from DTO-like or service-like classes placed in the same directory.

### Play Jobs

The plugin analyzes Play jobs and how they are invoked.

Supported behavior includes:

- job detection
- trigger classification
- discovery of manual invocations
- quick documentation
- gutter icons
- completion for supported annotation values such as `@Every`
- a **Jobs** panel in the tool window

#### Job Inspections

- missing `doJob`
- suspicious `@Every` value
- job annotation on an invalid job class
- unreferenced job
- blocking startup job

### Play Cache

Cache support covers both Java code and templates.

#### Java Cache Calls

The plugin detects and classifies cache operations such as:

- reads
- writes
- conditional writes
- invalidations
- global clears
- read-or-compute patterns

When possible, it also extracts:

- cache key
- TTL
- value type
- config-key dependencies used to build the key or TTL

#### Template Cache Tags

The plugin scans `#{cache ...}` tags in templates and tracks:

- key
- expiration
- included fragments
- injected variables such as `cacheName`, `cacheExpiration`, and `isCached`

#### Cached Actions

`@CacheFor` on Play actions is also indexed and exposed through the cache model.

#### Cache IDE Support

- Java completion for cache keys
- template completion for cache keys
- Java and template inlay hints
- line markers
- quick documentation
- a dedicated **Cache** panel in the tool window

#### Cache Inspections

- cache tag without a key
- cache tag without an expiration
- empty expiration
- `Cache.set(...)` without TTL
- risky `Cache.clear()`
- suspicious TTL
- key read without a writer
- key written without a reader
- key written without an invalidation path

## Tool Window

The plugin adds a **Play v1 Toolkit** tool window with these tabs:

- `Status`
- `Routes`
- `Templates`
- `Models`
- `Jobs`
- `Cache`
- `Diagnostics`

This is not just a dashboard. It is the place where setup, execution, and structural inspection of the Play project come together.

### Status

The **Status** tab shows:

- whether the project was detected as Play 1
- the current `Play Home`
- detected Play version
- CLI runtime information
- dependency-resolution mode
- whether the Play run configuration exists
- Run and Debug entry points
- buttons for Play CLI commands
- current command state

### Routes

The **Routes** tab offers:

- a route tree
- grouping by controller or by path
- navigation to either the route line or the Java action
- response information derived from action analysis

### Templates

The **Templates** tab gives you a structured view of templates and custom tags with quick navigation.

### Models

The **Models** tab summarizes detected JPA models.

### Jobs

The **Jobs** tab summarizes detected Play jobs and how they are triggered.

### Cache

The **Cache** tab gathers static and dynamic cache usages, cached fragments, cached actions, and cache-focused diagnostics.

### Diagnostics

The **Diagnostics** tab currently focuses on route integrity:

- controllers referenced in `conf/routes` but not found in the project
- actions referenced in `conf/routes` but missing from the controller

## Settings

Under **Settings > Tools > Play v1 Toolkit**, the plugin exposes:

- the main `Play Home`
- automatic Play Home detection
- Play Home validation
- default `Play ID`
- default HTTP port
- default debug port
- auto-configure project on open
- an optional `Play Home for deps`
- download of a recommended Play distribution for dependency sync

## What Needs a Real Play Installation

Some features are naturally much better once a valid Play installation is configured and the project has been repaired:

- framework library attachment
- Play CLI execution
- application run and debug
- Java-side features that depend on proper Play class resolution

## What Still Works Without `Play Home`

A fair amount of the PSI-based support still works from project structure alone, even before a full runtime setup:

- `conf/routes`
- `conf/application.conf`
- `conf/messages*`
- part of the template support
- part of the JPA, job, and cache analysis

## Sample Application

The repository includes [sample-play1-app/](/home/pablo/projets/play1-intellij-plugin/sample-play1-app/) as a quick manual test project.

It is there so you can open a small Play 1 codebase in a sandbox IDE and verify the plugin end to end without setting up a full production-like application first.

## Developing the Plugin

### Useful Commands

```bash
./gradlew buildPlugin
./gradlew runIde
./gradlew test
./gradlew verifyPlugin
```

## Marketplace Publishing

The repository is prepared for JetBrains Marketplace publishing through GitHub Releases.

- Only a published GitHub release with a stable tag in the form `vX.Y.Z` can trigger Marketplace publication.
- GitHub prereleases are ignored on purpose.
- The published plugin version is taken from the release tag, not from the `-SNAPSHOT` version kept in `gradle.properties`.
- The workflow is gated by the `INTELLIJ_MARKETPLACE_PUBLISH` repository variable, so it can stay merged without publishing anything until you are ready.

There is one unavoidable manual step: JetBrains requires the first Marketplace upload to be created manually. After that first approved version exists, the release workflow can publish the next versions automatically.

### Manual Setup Required Before the First Automated Release

1. Create your JetBrains account and vendor profile on JetBrains Marketplace.
2. Accept the Marketplace developer agreement and complete the vendor information JetBrains asks for.
3. Generate a permanent Marketplace token.
4. Generate the plugin signing key, password, and certificate chain.
5. Store these GitHub secrets:
   - `PUBLISH_TOKEN`
   - `PRIVATE_KEY`
   - `PRIVATE_KEY_PASSWORD`
   - `CERTIFICATE_CHAIN`
6. Create the repository variable `INTELLIJ_MARKETPLACE_PUBLISH` and leave it set to `false` until the first manual upload has been approved.
7. Upload the first signed plugin version manually in Marketplace and complete the listing fields there: license, source code link, screenshots, tags, documentation, and issue tracker.
8. After that first version is approved, set `INTELLIJ_MARKETPLACE_PUBLISH=true`.

### Automated Release Flow

Once the manual setup is done, the publication flow is simple:

1. Bump the code as needed and merge to `main`.
2. Create a GitHub Release tagged `vX.Y.Z`.
3. The release workflow verifies the plugin, signs it, uploads the ZIP to the GitHub Release, and publishes the same version to the JetBrains Marketplace.

### Repository Structure

```text
src/main/kotlin/com/github/pablolec/play1toolkit/
  actions/       User actions and CLI orchestration
  config/        Application-level settings
  detection/     Project and Play Home detection
  inspection/    Cross-cutting Java inspections
  lineMarker/    Java <-> routes navigation and response markers
  playcache/     Play cache analysis
  playconfig/    application.conf support
  playjobs/      Play job analysis
  playjpa/       JPA models and YAML fixture support
  playmessages/  Message bundle support
  project/       Libraries, roots, CLI, managed runtimes
  render/        render() -> view navigation
  response/      Static action response analysis
  routes/        conf/routes custom language
  run/           Play 1 run configuration
  services/      Shared project services
  templates/     Template and custom tag support
  toolwindow/    Tool window UI

src/test/kotlin/
  Unit tests and IntelliJ Platform tests

sample-play1-app/
  Small Play 1 app for manual verification
```

### Test Strategy

The repository combines:

- plain unit tests
- IntelliJ Platform tests through `BasePlatformTestCase`
- lightweight Play 1 fixtures
- a sample application for manual checks in the IDE sandbox

## Known Limits

- The plugin is intentionally focused on Play Framework 1.x, not Play 2+.
- Several analyses rely on PSI heuristics. They are useful in practice, but they are still static analysis.
- Some features become much more reliable after project repair and dependency resolution.

## Who This Is For

This plugin is for teams that still own a Play 1 codebase and want IntelliJ to be helpful again instead of merely tolerable.

It is especially useful when you need to:

- navigate a large legacy codebase quickly
- reduce IDE false positives
- rename things with less fear
- understand old Play 1 flows without reconstructing everything by hand
- keep maintenance work moving on an application that modern tooling has mostly forgotten
