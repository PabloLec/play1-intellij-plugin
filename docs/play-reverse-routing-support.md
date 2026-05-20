# Play 1 Reverse Routing Support

## Overview

The Play v1 Toolkit plugin provides full IDE support for Play 1 reverse routing syntax in templates located under `app/views/`.

## Supported Syntax

### Action reverse routes

```html
@{Controller.action()}
@{Controller.action(arg1, arg2)}
@@{Controller.action()}
```

- `@{...}` generates a relative URL
- `@@{...}` generates an absolute URL

### Static asset routes

```html
@{'/public/stylesheets/main.css'}
@{'/public/images/logo.png'}
@@{'/public/javascripts/app.js'}
```

## Features

### Navigation (Ctrl+Click)

- `@{Users.show(user.id)}` — Ctrl+Click on `Users` navigates to the Java controller class; Ctrl+Click on `show` navigates to the Java action method.
- `@{'/public/stylesheets/main.css'}` — Ctrl+Click navigates to the file under `public/`.
- `@@{...}` — same as `@{...}`.

### Completion

- `@{` — proposes all `ControllerName.actionName` pairs from Play controller classes. Actions with a declared route show the HTTP method and path as tail text (e.g., `GET /users/{id}`).
- `@{'` or `@{"` — proposes all files under `public/`.
- `@@{` and `@@{'` — same as above.

### Quick Documentation

Hover or press F1 on a reverse route to see:

- **Route:** HTTP method and URL path from `conf/routes` (all matching routes listed)
- **Action:** `Controller.action` reference
- **URL type:** Relative (`@{}`) or Absolute (`@@{}`)
- **Response:** inferred response type (HTML template, JSON, redirect, etc.)

For static assets:
- **Resolved file:** absolute path to the asset
- **URL type:** Relative or Absolute

### Inspections

| Name | Level | Default | Description |
|---|---|---|---|
| Unknown Play v1 reverse route | WEAK WARNING | enabled | `@{Missing.index()}` — controller or action not found in the project |
| Play v1 action not declared in conf/routes | WEAK WARNING | enabled | `@{Users.helper()}` — Java method exists but has no route in `conf/routes` |
| Missing Play v1 static asset | WARNING | enabled | `@{'/public/missing.png'}` — file not found under `public/` |
| Suspicious reverse route argument count | INFORMATION | disabled | `@{Users.show()}` — route has path params but none provided |

### Find Usages

From a Java controller **class** or **action method**, Find Usages includes all `@{...}` occurrences in templates.

From a **route line** in `conf/routes`, Find Usages includes all `@{...}` occurrences in templates for that action.

### Rename Refactoring

- Renaming a Java **action method** updates all `@{Controller.oldName(...)}` to `@{Controller.newName(...)}` in templates.
- Renaming a Java **controller class** updates all `@{OldName.action(...)}` to `@{NewName.action(...)}` in templates.
- Renaming a **static asset file** updates `@{'/public/old-name.css'}` references in templates.

## Known Limitations

- Argument type validation is not performed (only count is checked, and only for simple expressions).
- Expressions with method calls or array access inside reverse route arguments are not inspected for argument count.
- Optional query-string parameters are not distinguished from required path parameters in the argument count inspection.
- Routes defined via `module:` or `staticDir:` are not considered as reverse route targets.
