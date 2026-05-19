# sample-play1-app

A minimal Play Framework 1.x sample application used for testing the Play v1 Toolkit plugin.

Based on the `yabe` (Yet Another Blog Engine) example from the official Play Framework 1 repository.

## Structure

```
app/
  controllers/
    Application.java   — main controller
    Posts.java         — posts controller
  models/
    Post.java          — Post JPA entity
  views/
    Application/index.html
    Application/show.html
    Posts/show.html
conf/
  application.conf     — app config (dev mode, in-memory DB)
  routes               — HTTP routes
  dependencies.yml     — Ivy dependencies
test/
  ApplicationTest.java — functional test (Play test framework)
lib/                   — empty, for additional JARs
```

## Purpose

This sample is used to:
- Test Play 1 project detection
- Test library attachment (import play.mvc.*)
- Test source root configuration
- Test routes parsing and navigation
- Test conf/routes syntax highlighting

It is NOT intended to be run as a real application (requires a Play 1 installation and database).
