# Todo — Play 1 Toolkit

> Ce fichier est la liste vivante des tâches. Il doit rester à jour à chaque étape.
> Statuts : `[ ]` à faire · `[~]` en cours · `[x]` terminé · `[>]` reporté · `[!]` bloquant

---

## Lot 0 — Documentation initiale

- [x] Lire et analyser `expression-de-besoin.md`
- [x] Créer `docs/todo.md`
- [x] Créer `docs/runlog.md`
- [x] Créer `docs/work-breakdown.md`
- [x] Créer `docs/architecture.md`
- [x] Créer `docs/testing.md`
- [x] Créer `docs/intellij-platform-notes.md`
- [x] Créer `docs/play1-assumptions.md`
- [x] Créer `docs/adr/README.md`
- [x] Créer ADR-001 à ADR-005
- [ ] Commit `docs: initialize project work documentation and ADRs`

---

## Lot 1 — Bootstrap projet IntelliJ plugin ✓ DONE

- [x] Créer `build.gradle.kts` avec IntelliJ Platform Gradle Plugin 2.x
- [x] Créer `settings.gradle.kts`
- [x] Créer `gradle.properties`
- [x] Créer le wrapper Gradle (`gradle/wrapper/`)
- [x] Créer `src/main/resources/META-INF/plugin.xml` minimal
- [x] Créer la structure de packages Kotlin (actions, config, detection, model, project, run, routes, services, toolwindow, inspection)
- [x] Créer `README.md`
- [x] Créer `CHANGELOG.md`
- [x] Créer `.gitignore`
- [x] Créer `.github/workflows/build.yml` (CI)
- [x] `./gradlew buildPlugin` → BUILD SUCCESSFUL
- [x] `./gradlew test` → 16 tests passent
- [x] Commit effectué

---

## Lot 2 — Détection projet Play 1 ✓ DONE (inclus dans Lot 1)

- [x] Créer `Play1ProjectDetector.kt` (logique pure, sans IDE)
- [x] Créer `Play1ProjectService.kt` (Project Service)
- [x] Créer `Play1StartupActivity.kt` (ProjectActivity)
- [x] Enregistrer le service et l'activity dans `plugin.xml`
- [x] Créer `Play1ProjectDetectorTest.kt` (6 tests)
- [x] Vérifier tests passent

---

## Lot 3 — Settings Play Home ✓ DONE (inclus dans Lot 1)

- [x] Créer `Play1Settings.kt` (PersistentStateComponent, application-level)
- [x] Créer `Play1SettingsConfigurable.kt`
- [x] Créer `Play1SettingsPanel.kt` (UI)
- [x] Implémenter `Play1HomeDetector.kt` (auto-detect)
- [x] Implémenter `Play1HomeValidator.kt` (validation JAR principal)
- [x] `Play1HomeValidatorTest.kt` (5 tests avec play-stub.jar)
- [x] Enregistrer dans `plugin.xml`

---

## Lot 4 — Repair Project Setup ✓ DONE (inclus dans Lot 1)

- [x] Créer `RepairProjectSetupAction.kt`
- [x] Créer `Play1LibraryManager.kt` (attache JARs Play + lib/)
- [x] Créer `Play1SourceRootManager.kt` (source roots)
- [x] Créer `Play1RunConfigManager.kt` (crée run config)
- [x] Créer `RepairReport.kt` (modèle rapport)
- [x] Enregistrer action dans `plugin.xml` (menu Tools > Play 1)
- [ ] Tests plateforme `RepairProjectSetupActionTest` — TODO Lot 9

---

## Lot 5 — Run/Debug configuration ✓ DONE (inclus dans Lot 1)

- [x] Créer `Play1RunConfigurationType.kt`
- [x] Créer `Play1ApplicationRunConfiguration.kt`
- [x] Créer `Play1ApplicationConfigurationFactory.kt`
- [x] Créer `Play1ApplicationRunState.kt`
- [x] Créer `Play1RunConfigurationEditor.kt`
- [x] Enregistrer dans `plugin.xml`
- [ ] Tester run configuration dans sandbox — TODO (nécessite Play Home réel)

---

## Lot 6 — Sample Play 1 app + fixtures de test ✓ DONE (inclus dans Lot 1)

- [x] Copier/adapter `yabe` depuis `/tmp/play1-master/samples-and-tests/yabe/`
- [x] Créer `play-stub.jar` (play.Play, play.mvc.Controller, play.server.Server)
- [x] Placer stub jar dans `src/test/resources/stubs/`

---

## Lot 7 — Support conf/routes

- [ ] Créer `RoutesLanguage.kt` + `RoutesFileType.kt`
- [ ] Créer `RoutesLexer.kt` (JFlex ou manuel)
- [ ] Créer `RoutesSyntaxHighlighter.kt`
- [ ] Créer `RoutesParserDefinition.kt` (PSI basique)
- [ ] Créer `RoutesCompletionContributor.kt`
- [ ] Créer `RoutesNavigationContributor.kt` (Ctrl+Click → Java)
- [ ] Créer `RoutesAnnotator.kt`
- [ ] Enregistrer dans `plugin.xml`
- [ ] Tests `RoutesParserTest`, `RoutesNavigationTest`
- [ ] Commit `feat: add conf/routes file type with syntax highlighting and navigation`

---

## Lot 8 — Tool Window Play 1

- [ ] Créer `Play1ToolWindowFactory.kt`
- [ ] Créer `ProjectStatusPanel.kt`
- [ ] Créer `RoutesTreePanel.kt`
- [ ] Créer `DiagnosticsPanel.kt`
- [ ] Enregistrer dans `plugin.xml`
- [ ] Tester dans sandbox
- [ ] Commit `feat: add Play 1 Tool Window`

---

## Lot 9 — Tests complets

- [ ] `Play1ProjectDetectorTest` (layouts variés)
- [ ] `RoutesParserTest` (parsing valide/invalide)
- [ ] `Play1LibraryManagerTest` (scan JARs)
- [ ] `RepairReportTest` (construction rapport)
- [ ] `RepairProjectSetupActionTest` (platform test)
- [ ] `RoutesNavigationTest` (platform test)
- [ ] `Play1SettingsTest` (persistance)
- [ ] Commit `test: add unit and platform tests for core plugin features`

---

## Points bloquants connus

_Aucun pour l'instant._

---

## Reporté / hors MVP initial

- [ ] Support templates Play (`.html`) — Lot futur
- [ ] Support `application.conf` (complétion, doc) — Lot futur
- [ ] Support `dependencies.yml` — Lot futur
- [ ] Tool window Services integration — Lot futur
- [ ] Onboarding mode — Lot futur
- [ ] Export Markdown overview — Lot futur
- [ ] Route Runner / HTTP Client — Lot futur
- [ ] Find usages enrichi (route → controller → template) — Lot futur
