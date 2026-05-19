# Todo — Play v1 Toolkit

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
- [x] Enregistrer action dans `plugin.xml` (menu Tools > Play v1 Toolkit)
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

## Lot 7 — Support conf/routes ✓ DONE

- [x] Créer `RoutesLanguage.kt` + `RoutesFileType.kt`
- [x] Créer `RoutesLexer.kt` (manuel, machine à états 6 états)
- [x] Créer `RoutesSyntaxHighlighter.kt` + `RoutesSyntaxHighlighterFactory.kt`
- [x] Créer `RoutesParserDefinition.kt` (PSI : RoutesFile > RoutesRouteElement)
- [x] Créer `psi/RoutesFile.kt` + `psi/RoutesRouteElement.kt`
- [x] Créer `RoutesReferenceContributor.kt` + références controller/action
- [x] Créer `RoutesCompletionContributor.kt`
- [x] Créer `RoutesAnnotator.kt`
- [x] Enregistrer dans `plugin.xml` (fileType, syntaxHighlighter, parserDef, referenceContributor, completion, annotator)
- [x] `RoutesLexerTest.kt` — 17 tests unitaires (lexer)
- [x] Commit effectué

---

## Lot 8 — Tool Window Play v1 Toolkit ✓ DONE

- [x] Créer `Play1ToolWindowFactory.kt` (3 onglets : Status / Routes / Diagnostics)
- [x] Créer `ProjectStatusPanel.kt` (Play detected, Play Home, version, run config)
- [x] Créer `RoutesTreePanel.kt` (PSI routes → JTree)
- [x] Créer `DiagnosticsPanel.kt` (résolution controllers/actions)
- [x] Enregistrer dans `plugin.xml` (toolWindow anchor="right")
- [x] Commit effectué

---

## Lot 9 — Tests complets ✓ DONE

- [x] `Play1ProjectDetectorTest` (6 tests existants — layouts variés)
- [x] `Play1ProjectDetectorFixturesTest` (4 tests — valide les fixtures statiques)
- [x] `RoutesLexerTest` (17 tests — lexer GET/POST/*/staticDir/module/params)
- [x] `Play1LibraryManagerTest` (9 tests — scan JARs, URLs, comptage)
- [x] `RepairReportTest` (5 tests existants — construction rapport)
- [x] `Play1HomeValidatorTest` (5 tests existants — validation Play Home)
- [x] Fixtures enrichies : play1-standard, play1-minimal, not-play1
- [ ] `RepairProjectSetupActionTest` (platform test) — reporté post-MVP
- [ ] `RoutesNavigationTest` (platform test) — reporté post-MVP
- [ ] `Play1SettingsTest` (persistance) — reporté post-MVP
- [x] Commit effectué

**Total : 46 tests, 0 échecs**

---

## Lot 13 — Navigation render() → vue implicite ✓ DONE

- [x] `render/Play1ViewUtils.kt` (isPlayController, findViewFile, findRoutesForAction, implicitViewPath)
- [x] `render/Play1RenderViewGotoHandler.kt` (GotoDeclarationHandler)
- [x] `Play1ViewUtilsTest.kt` (6 tests unitaires)
- [x] Fixtures enrichies : `app/views/Application/index.html` + `show.html`
- [x] Enregistrement `gotoDeclarationHandler` dans `plugin.xml`
- [x] Commit effectué

---

## Lot 14 — Gutter icons controller ↔ routes ✓ DONE

- [x] `RoutesRouteElement.getPath()` — expose le chemin de la route
- [x] `lineMarker/Play1ControllerLineMarkerProvider.kt` (JAVA → routes)
- [x] `lineMarker/Play1RoutesLineMarkerProvider.kt` (Routes → controller Java)
- [x] Enregistrement `codeInsight.lineMarkerProvider` ×2 dans `plugin.xml`
- [x] Commit effectué

---

## Lot 15 — Inspections avancées ✓ DONE

- [x] `inspection/Play1MissingViewInspection.kt` (WARNING sur render() sans vue)
- [x] `inspection/CreateMissingViewQuickFix.kt` (crée la vue + ouvre l'éditeur)
- [x] Enregistrement `localInspection` dans `plugin.xml`
- [x] Commit effectué

**Total : 52 tests, 0 échecs**

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
