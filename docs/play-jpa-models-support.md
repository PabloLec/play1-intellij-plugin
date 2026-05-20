# Play JPA Models Support

`Play v1 Toolkit` adds Play 1 oriented persistence intelligence on top of IntelliJ's native Java support. The goal is not to reimplement Hibernate, but to understand the conventions that appear constantly in Play 1 legacy applications:

- `play.db.jpa.Model`
- `play.db.jpa.GenericModel`
- `play.db.jpa.JPABase`
- `@Entity` classes under `app/models`
- Play static finder helpers such as `find`, `findById`, `all`, `count`, `delete`
- Play YAML fixtures used by tests and bootstrap data

## Supported conventions

The plugin keeps two different views of `app/models`:

- **Play JPA models** used by finder / fixture / JPA-aware features
- a broader **Models** tool window view that classifies everything under `app/models`

Play JPA models are detected conservatively from strong persistence signals such as:

- direct extension of `play.db.jpa.Model`
- direct extension of `play.db.jpa.GenericModel`
- `@Entity` with direct persistence signals
- direct JPA field annotations such as `@Id`, `@Column`, `@ManyToOne`

This avoids treating every class under `app/models` as a persisted entity.

## What the plugin adds

### Model detection and documentation

- gutter icon on Play JPA model classes
- gutter icon on JPA relation fields
- quick documentation on model classes and relation fields
- a categorized `Models` tab in the Play v1 Toolkit tool window

### Play finder intelligence

Supported static model helpers include:

- `User.findById(id)`
- `User.find("byEmail", email)`
- `User.find("email = ?", email)`
- `User.findAll()`
- `User.all().fetch()`
- `User.count()`

Available IDE features:

- quick documentation on finder calls
- finder inlay hints
- navigation from `byEmail` or `email = ?` to the model field
- completion inside `find("by<caret>")`
- weak inspections for:
  - unknown fields in Play finders
  - weak JPQL-like field mistakes
  - placeholder count mismatch

## YAML fixtures

The plugin recognizes Play fixture-style YAML files in locations such as:

- `conf/*.yml`
- `conf/*.yaml`
- `test/*.yml`
- `test/*.yaml`

Fixture entries are detected when top-level keys use the Play pattern:

```yaml
User(bob):
  email: bob@example.com
```

Available IDE features:

- `User` resolves to the `User` model class
- fixture field keys resolve to model fields
- relation values can resolve to fixture aliases
- completion for:
  - model names
  - field names
  - relation target aliases
- weak inspections for:
  - unknown fixture model
  - unknown fixture field
  - unknown relation target alias
  - duplicate fixture alias

## Templates integration

When a controller passes a typed variable to `render(...)` or `renderTemplate(...)`, template completion and navigation reuse the inferred Java type.

Example:

```java
User user = User.findById(id);
render(user);
```

In the matching Play template:

```html
${user.<caret>}
```

the plugin can propose model fields such as `email`, `name`, or relation fields such as `orders`.

## Response analysis integration

The response analyzer already computes rich JSON details from the PSI type of the expression passed to `renderJSON(...)`.

For a Play JPA model:

```java
User user = User.findById(id);
renderJSON(user);
```

the response marker can display `JSON<User>`.

## Tool window

The `Models` tab classifies the contents of `app/models` into:

- Persistent Models
- DTOs / View Models
- Business Objects
- Services / Helpers
- Enums
- Unclassified

Persistent entries keep the richer persistence details:

- fields
- relations
- fixture counts
- usages count

Each entry also shows:

- confidence
- classification reasons

Double-click opens the target class, model field, or relation field.

## Inspections

The feature currently contributes these Play-specific inspections:

- Play JPA model without `@Entity`
- Play `@Entity` under `app/models` not extending `Model` or `GenericModel`
- unknown field in Play finder
- Play finder parameter count mismatch
- unknown model in Play fixture
- unknown field in Play fixture
- unknown fixture alias in Play fixture relation
- duplicate fixture alias in Play fixture

All inspections are intentionally conservative and tuned for legacy Play 1 codebases.

## Limits

- the plugin does not parse full JPQL or HQL
- query string analysis is intentionally simple and safe
- YAML relation target detection relies on model relation metadata
- dynamic finder or query string construction is treated conservatively
- the `Models` tab is descriptive for legacy projects; it does not treat services or DTOs under `app/models` as errors
- the feature enriches IntelliJ's Java support; it does not replace native refactorings or ORM understanding
