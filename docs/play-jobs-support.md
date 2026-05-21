# Play Jobs Support

`Play v1 Toolkit` surfaces the non-HTTP runtime layer of a Play 1 application — startup hooks, shutdown hooks, scheduled tasks, cron-like tasks and manual asynchronous invocations — through the same kind of intelligence the plugin already provides for routes, templates and JPA models.

The goal is to answer at a glance the question routes alone cannot answer: **what runs in this app outside of HTTP endpoints?**

Play 1 only. The detection logic is conservative enough to remain useful on legacy code where the classpath is partially broken.

## Supported conventions

A class is considered a Play job when at least one of these signals is present:

- it extends `play.jobs.Job`
- it is annotated with `@OnApplicationStart`
- it is annotated with `@OnApplicationStop`
- it is annotated with `@Every("...")`
- it is annotated with `@On("...")`

Weaker positional signals are also recognized to surface candidates in legacy projects:

- the file lives under `app/jobs/`
- the class name ends with `Job`, `Batch`, `Task` or `Scheduler`

Detection runs as a per-file PSI walk cached on `PsiModificationTracker.MODIFICATION_COUNT`. No `ClassInheritorsSearch` or `AnnotatedElementsSearch` are issued for the classification pass — invocation lookup is performed lazily per job class and cached independently.

## Categories

Each detected job is placed in one of:

| Category            | Trigger                              |
|---------------------|--------------------------------------|
| Startup             | `@OnApplicationStart`                |
| Shutdown            | `@OnApplicationStop`                 |
| Scheduled · every   | `@Every("…")`                        |
| Scheduled · cron    | `@On("…")` (Quartz expression)       |
| Manual / async      | extends `play.jobs.Job`, no trigger, ≥1 manual invocation |
| Unknown scheduling  | only the positional fallback matched |

Each job is also marked with a confidence level — `High`, `Medium`, or `Low`:

- **High** — extends `play.jobs.Job` AND (≥1 trigger annotation OR ≥1 `doJob` / `doJobWithResult`)
- **Medium** — exactly one of: extends Job, OR carries a trigger annotation
- **Low** — only the positional fallback (under `app/jobs/`, name ends with `Job`/`Batch`/`Task`/`Scheduler`)

## Manual invocations

The plugin recognizes the standard Play 1 invocation shapes:

```java
new ImportJob().now();
new ReminderJob().in("5mn");
new BillingJob().at("…");
new ReportJob().afterRequest();
```

The kind is preserved on each invocation entry (`NOW`, `IN`, `AT`, `AFTER_REQUEST`, `NEW_ONLY`).

## What the plugin adds

### Gutter icons

- on a Job class identifier — category, trigger, execution method
- on the `@Every` / `@On` / `@OnApplicationStart` / `@OnApplicationStop` annotation name — schedule explanation
- on `doJob()` / `doJobWithResult()` — execution method marker
- on `new SomeJob()` — navigation arrow back to the Job class

### Tool Window

A `Jobs` tab is added to the Play v1 Toolkit tool window, with a tree categorized by Startup / Scheduled (Every, Cron) / Shutdown / Manual / Async / Unknown scheduling. Each job node shows the class name, confidence and detected manual-invocation count, with child nodes for triggers, execution methods, and invocations.

Double-click navigates to the underlying PSI element. The right-click context menu offers:

- Open Class
- Open `doJob()`
- Copy Job Name
- Find Invocations — delegates to IntelliJ's Find Usages tool window scoped to the Job class

### Quick Documentation

`Ctrl+Q` on a Job class identifier returns a structured panel with class, category, confidence, classification reasons, triggers, execution methods, manual invocation count and — when detectable from the class body — configuration keys read (`Play.configuration.getProperty("…")`), JPA models referenced, and `Messages.get("…")` keys.

### Inspections

All inspections are conservative; the two informational ones ship disabled by default.

| Inspection                                | Level         | Default  |
|-------------------------------------------|---------------|----------|
| Play Job without `doJob()` / `doJobWithResult()` | WEAK WARNING  | enabled  |
| Suspicious `@Every` value (empty, or not matching `^\d+(s\|mn\|h\|d)$`) | WEAK WARNING  | enabled  |
| Job annotation on class that does not extend `play.jobs.Job` | WEAK WARNING  | enabled  |
| Play Job under `app/jobs/` with no trigger or invocation | INFORMATION   | disabled |
| Startup job may perform blocking work (sync `@OnApplicationStart` containing `Thread.sleep`, infinite loop, or `URL.openConnection` / `HttpURLConnection`) | INFORMATION   | disabled |

### Completion

Inside `@Every("<caret>")`, the standard Play 1 duration values are proposed: `1s`, `10s`, `1mn`, `5mn`, `1h`, `1d`. The plugin does not attempt to validate every Play 1 duration syntax — only the most common values are completed.

### Find Usages

Right-click on any Job node in the Tool Window → `Find Invocations` opens IntelliJ Find Usages scoped to the Job class. Manual invocations show up naturally as Java references, since `new MyJob()` is itself a PSI reference to the class.

## Not supported / future work

- **No execution action.** Running a Play 1 Job outside of the Play runtime is fragile (Hibernate, the Play classloader, fixtures, configuration profiles all rely on `play run`). The plugin does not offer "Run Play Job" — only the safe actions listed above.
- **No interprocedural analysis.** Quick Documentation enrichment looks only at literals directly contained in the Job class body. Calls into helper services, util methods or other classes are intentionally not followed.
- **No Play 2 / Play 3.** Detection is hard-coded to `play.jobs.*` from Play 1.x.
- **No deep `@On` cron validation.** A cron expression is treated as opaque text — the plugin does not check field counts or value ranges.

## Examples

```java
// Startup
@OnApplicationStart
public class BootstrapJob extends Job {
    public void doJob() { /* ... */ }
}

// Scheduled, fixed interval
@Every("1h")
public class ImportJob extends Job {
    public void doJob() { /* ... */ }
}

// Cron
@On("0 0 3 * * ?")
public class BillingJob extends Job {
    public void doJob() { /* ... */ }
}

// Shutdown
@OnApplicationStop
public class CleanupJob extends Job {
    public void doJob() { /* ... */ }
}

// Manual
public class MailerJob extends Job {
    private final String recipient;
    public MailerJob(String recipient) { this.recipient = recipient; }
    public void doJob() { /* ... */ }
}

// In a controller:
new MailerJob("alice@example.com").now();
```
