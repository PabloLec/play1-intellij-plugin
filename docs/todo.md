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

## Lot 1 — Bootstrap projet IntelliJ plugin

- [ ] Créer `build.gradle.kts` avec IntelliJ Platform Gradle Plugin 2.x
- [ ] Créer `settings.gradle.kts`
- [ ] Créer `gradle.properties`
- [ ] Créer le wrapper Gradle (`gradle/wrapper/`)
- [ ] Créer `src/main/resources/META-INF/plugin.xml` minimal
- [ ] Créer la structure de packages Kotlin
- [ ] Créer `README.md`
- [ ] Créer `CHANGELOG.md`
- [ ] Créer `.gitignore`
- [ ] Créer `.github/workflows/build.yml` (CI)
- [ ] Vérifier `./gradlew buildPlugin` → BUILD SUCCESSFUL
- [ ] Commit `chore: bootstrap IntelliJ plugin project`

---

## Lot 2 — Détection projet Play 1

- [ ] Créer `Play1ProjectDetector.kt` (logique pure, sans IDE)
- [ ] Créer `Play1ProjectService.kt` (Project Service)
- [ ] Créer `Play1StartupActivity.kt` (ProjectActivity)
- [ ] Enregistrer le service et l'activity dans `plugin.xml`
- [ ] Créer fixtures de test (`src/test/resources/fixtures/`)
- [ ] Créer `Play1ProjectDetectorTest.kt`
- [ ] Vérifier tests passent
- [ ] Commit `feat: add Play 1 project detection service and startup activity`

---

## Lot 3 — Settings Play Home

- [ ] Créer `Play1Settings.kt` (PersistentStateComponent, application-level)
- [ ] Créer `Play1SettingsConfigurable.kt`
- [ ] Créer `Play1SettingsPanel.kt` (UI)
- [ ] Implémenter auto-detect Play Home
- [ ] Implémenter validation Play Home (JAR principal)
- [ ] Enregistrer dans `plugin.xml`
- [ ] Commit `feat: add Play Home settings with auto-detect and validation`

---

## Lot 4 — Repair Project Setup

- [ ] Créer `RepairProjectSetupAction.kt`
- [ ] Créer `Play1LibraryManager.kt` (attache JARs Play + lib/)
- [ ] Créer `Play1SourceRootManager.kt` (source roots)
- [ ] Créer `Play1RunConfigManager.kt` (crée run config)
- [ ] Créer `RepairReport.kt` (modèle rapport)
- [ ] Créer `RepairReportDialog.kt` (affichage rapport)
- [ ] Enregistrer action dans `plugin.xml` (menu Tools > Play 1)
- [ ] Tests plateforme `RepairProjectSetupActionTest`
- [ ] Commit `feat: implement Repair Play 1 Project Setup action`

---

## Lot 5 — Run/Debug configuration

- [ ] Créer `Play1RunConfigurationType.kt`
- [ ] Créer `Play1ApplicationRunConfiguration.kt`
- [ ] Créer `Play1ApplicationConfigurationFactory.kt`
- [ ] Créer `Play1ApplicationRunState.kt`
- [ ] Enregistrer dans `plugin.xml`
- [ ] Tester run configuration dans sandbox
- [ ] Commit `feat: add Play 1 run and debug configuration type`

---

## Lot 6 — Sample Play 1 app + fixtures de test

- [ ] Copier/adapter `yabe` depuis `/tmp/play1-master/samples-and-tests/yabe/`
- [ ] Nettoyer le sample (garder le minimal utile)
- [ ] Créer `play-stub.jar` (play.Play, play.mvc.Controller, play.server.Server)
- [ ] Placer stub jar dans `src/test/resources/stubs/`
- [ ] Documenter dans `docs/testing.md`
- [ ] Commit `test: add sample Play 1 app fixture and play stub jar`

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
