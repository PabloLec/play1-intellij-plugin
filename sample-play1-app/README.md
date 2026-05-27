# sample-play1-app

This is a small Play Framework 1.x application included in the repository so you can test **Play v1 Toolkit** manually inside a sandbox IntelliJ instance.

## Why It Exists

This project is not meant to be a realistic business app, and it is not the main plugin documentation either. Its job is much simpler: give the plugin a real Play 1 project layout to work with, so the core setup and navigation features can be checked quickly.

It is useful for verifying:

- Play 1 project detection
- IntelliJ project repair
- Play library attachment
- source root configuration
- `conf/routes` support
- navigation between routes, controllers, and views
- basic scenarios for models, jobs, and templates

## Structure

```text
app/
  controllers/
    Application.java
    Posts.java
  jobs/
    BillingJob.java
    BootstrapJob.java
    CleanupJob.java
    ImportJob.java
    MailerJob.java
  models/
    Post.java
  views/
    Application/index.html
    Application/show.html
    Posts/show.html
conf/
  application.conf
  dependencies.yml
  routes
public/
  images/logo.png
  stylesheets/main.css
test/
  ApplicationTest.java
```

## Suggested Use

1. Start a sandbox IDE with `./gradlew runIde`.
2. Open `sample-play1-app/` in that sandbox.
3. Check that the plugin detects the project as Play 1.
4. Configure `Play Home` if needed.
5. Run **Repair Project Setup**.
6. Then check navigation, inspections, and the tool window.

## Limits

- Some plugin features work without a full Play installation, but not all of them do.
- Run/debug, `play deps`, and classpath-dependent features still require a valid Play 1 distribution.
- This sample is deliberately small. It is good for smoke testing, not for demonstrating every edge case the plugin supports.

## Main Documentation

The full plugin documentation lives in the [root README](/home/pablo/projets/play1-intellij-plugin/README.md).
