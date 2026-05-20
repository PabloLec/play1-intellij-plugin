# Play Templates And Tags Support

## Goal

`Play v1 Toolkit` adds Play Framework 1 template intelligence on top of IntelliJ's existing HTML/XML editing support.

The feature targets Play 1 templates stored under `app/views` and custom tags stored under `app/views/tags`.

The objective is to keep native editor comfort for HTML/XML while adding Play-specific navigation, completion, documentation and lightweight inspections.

## Supported Syntax

The plugin recognizes the most common Play 1 template constructs:

- Expressions: `${user.name}`, `${account?.label}`
- Inline Groovy blocks: `%{ ... }%`
- Layout and include tags: `#{extends 'main.html' /}`, `#{include 'Users/_row.html' /}`
- Layout variables: `#{set title:'Home' /}`, `#{get 'title' /}`, `#{doLayout /}`
- Built-in tags such as `list`, `if`, `form`, `script`, `stylesheet`, `cache`, `secure`
- Custom tags such as `#{userCard user:user /}` and nested tag names such as `#{layout.menu /}`
- Reverse routing: `@{Users.show(user.id)}` and `@@{Users.show(user.id)}`
- Static assets: `@{'/public/stylesheets/main.css'}`
- Existing Play messages syntax remains supported: `&{'app.title'}`

## IntelliJ Integration

The implementation uses a pragmatic overlay strategy instead of replacing HTML/XML support:

- PSI references on HTML/XML text nodes for template paths, custom tags, reverse routes and static assets
- PSI references on Java string literals for `renderTemplate("...")`
- Completion contributors for tags, template paths, reverse routes, assets, variables and custom tag parameters
- Goto declaration handlers for template navigation in both directions
- Documentation targets for templates, built-in tags, custom tags, reverse routes, assets and template variables
- Line markers for template-to-action and tag usage discovery
- Usage searchers so Find Usages includes template/tag/reverse-route usages

This keeps the editor responsive and preserves IntelliJ's default HTML/XML experience.

## Navigation

Available navigation includes:

- `render()` and `renderTemplate("...")` to template files
- `#{extends ...}` and `#{include ...}` to referenced templates
- custom tag usage to `app/views/tags/...`
- reverse route usage to Java controller actions
- static asset usage to files under `public/`
- template file to likely rendering action via gutter or declaration navigation

## Completion

Completion is available for:

- tag names after `#{`
- include and extends paths
- reverse routes after `@{`
- static assets after `@{'/public/...`
- variables inside `${...}`
- custom tag parameters inside tag calls
- Java `renderTemplate("...")` paths

## Inspections

The plugin provides lightweight inspections intended to help in legacy Play 1 codebases without becoming noisy:

- missing template file
- unknown Play tag
- unknown reverse route target
- missing static asset
- unknown template variable
- unbalanced Play tag blocks
- missing implicit or explicit rendered template from Java controllers

Diagnostics are intentionally conservative. Weak warnings are preferred over aggressive false positives.

## Custom Tags

Custom tags are indexed from `app/views/tags`.

Supported conventions:

- `app/views/tags/userCard.html` → `#{userCard /}`
- `app/views/tags/layout/menu.html` → `#{layout.menu /}`

Tag parameter discovery is heuristic and based on `_param` usages such as `${_user}` inside the tag template.

## Variables

Template variable completion and unknown-variable checks are based on:

- implicit Play variables such as `request`, `params`, `session`, `flash`, `errors`
- controller `render(...)` and `renderTemplate(...)` arguments
- `#{list items:..., as:'item'}` local block variables

The implementation is intentionally simple and aimed at common Play 1 patterns rather than full Groovy data-flow analysis.

## Tool Window

The Play tool window now includes a `Templates` tab with:

- indexed templates grouped by path
- indexed custom tags grouped by qualified name
- quick navigation to files
- direct navigation to likely rendering actions for conventional templates

## Known Limits

The feature is designed for reliable, high-signal Play 1 support, not perfect template-language parsing.

Current limits include:

- no full Groovy parser for template expressions
- best-effort template-to-action inference based on Play 1 conventions
- conservative validation for complex reverse routing and custom tag parameter flows
- JSON/TXT templates receive less editor-specific structure than HTML/XML templates

Even with those limits, the feature covers the core Play 1 template workflows needed for day-to-day maintenance on legacy applications.
