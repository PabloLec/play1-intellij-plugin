# Play Cache Intelligence

`Play v1 Toolkit` now maps the Play 1 cache layer alongside routes, templates, JPA models and jobs. The feature is intentionally limited to Play Framework 1.x and focuses on the native cache mechanisms that appear in real legacy applications:

- template fragments via `#{cache ...}`
- cached actions via `@CacheFor`
- Java cache API calls via `play.cache.Cache`

The goal is operational clarity: **what is cached, with which key, for how long, where it is read, where it is written, and where it is invalidated**.

## Supported mechanisms

### Template cache fragments

The plugin scans Play templates under `app/views` and detects:

```html
#{cache 'home-page', for:'10mn'}
    #{include 'Application/home.html' /}
#{/cache}
```

Supported key shapes:

- `static` — exact literal key such as `home-page`
- `pattern` — mixed literal/dynamic content such as `user:${...}`
- `dynamic` — expression-only keys such as `${cacheName}` or `someExpression`

Supported TTL shapes:

- `static` — exact literal duration such as `10mn`
- `dynamic` — expression-based duration such as `${cacheExpiration}`
- `absent` — no explicit `for:` parameter

Included templates inside the cached body are surfaced when they can be detected from `#{include '...' /}`.

### Cached actions

The plugin detects both:

```java
@CacheFor("1h")
public static void index() { ... }
```

and:

```java
@play.cache.CacheFor("1h")
public static void index() { ... }
```

For each cached action it exposes:

- TTL
- matching routes from `conf/routes`
- response kind via the existing response analyzer
- response hint enrichment in routes, for example `HTML · cached 1h`
- tool window and quick documentation entries

### Java cache API

The plugin classifies `play.cache.Cache` calls into the main maintenance categories:

- `read` — `get`
- `read / compute` — `getOrElse`
- `write` — `set`
- `write if absent` — `add`, `safeAdd`
- `write if present` — `replace`, `safeReplace`
- `invalidation` — `delete`, `safeDelete`
- `global clear` — `clear`
- `mutation` — `incr`, `decr`

When the call shape is simple enough, the plugin also extracts:

- cache key
- TTL
- value expression
- value type
- configuration-backed TTL keys such as `Play.configuration.getProperty("cache.dashboard.ttl")`

## IDE surfaces

### Quick Documentation

`Ctrl+Q` works on:

- `#{cache ...}` fragments
- `@CacheFor`
- `Cache.*` calls

Documentation shows key/TTL classification, template path or action name, related route information, response kind, related usage counts, and config-backed TTL details when available.

### Inlay hints

Optional inlay hints are available for:

- Java cache calls: `cache read`, `cache write · 10mn`, `cache CLEAR`
- template cache fragments: `CACHE dashboard · 10mn`, `CACHE dynamic ${cacheExpiration}`
- routes response hints enriched with cache metadata when the action uses `@CacheFor`

### Gutter icons

The plugin adds lightweight gutter markers for:

- cached actions
- Java cache calls
- template cache fragments
- global clears

### Tool Window

The `Cache` tab in the Play v1 Toolkit tool window groups data into:

- Template fragments
- Cached actions
- Static keys
- Dynamic usages
- Global clears
- Diagnostics

Double-click opens the exact PSI location. The panel shows route, response, TTL, value type and config-backed TTL details when available.

### Completion

Static cache keys already seen elsewhere in the project are proposed in:

- `Cache.get("<caret>")`
- `Cache.delete("<caret>")`
- `#{cache '<caret>'}`

Completion stays conservative and only suggests known static keys.

## Inspections

The feature adds conservative Play 1 specific inspections:

- cache tag without key
- cache tag without explicit expiration
- cache tag with empty expiration
- cache write without explicit expiration
- suspicious empty TTL on `@CacheFor` or `Cache.*`
- global `Cache.clear()` usage
- static key read without writer
- static key written without reader
- static key written without invalidation

The noisier maintenance inspections ship disabled by default.

## Known limits

- Play 1 only. No Play 2 or Play 3 support is added.
- No heavy interprocedural analysis.
- Dynamic keys remain explicitly dynamic; the plugin does not guess exact runtime keys.
- Template cache detection is raw-text based, by design, to stay fast and resilient on legacy files.
- `Cache.clear()` is treated as a global risk marker rather than something that can be resolved to a single key.

## Example

```java
@CacheFor("1h")
public static void index() {
    Cache.set("front-post", post, Play.configuration.getProperty("cache.frontPost.ttl"));
    render(post);
}
```

```html
#{cache "teaser:${post.id}", for:'5mn'}
    #{include 'Application/_teaser.html' /}
#{/cache}
```
