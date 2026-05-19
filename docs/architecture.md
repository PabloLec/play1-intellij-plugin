# Architecture — Play 1 Toolkit

> Ce document décrit l'architecture retenue pour le plugin. Il doit évoluer à mesure que le projet avance.
> Les décisions structurantes sont détaillées dans `docs/adr/`.

---

## Vue d'ensemble

Le plugin est un plugin IntelliJ IDEA Ultimate écrit en Kotlin, buildé avec Gradle et l'IntelliJ Platform Gradle Plugin 2.x.

Il est organisé en modules logiques bien séparés, sans sur-ingénierie. L'objectif est de garder les classes petites, les responsabilités claires, et la logique Play 1 isolée des APIs IntelliJ.

---

## Packages et responsabilités

```
com.github.pablolec.play1toolkit/
│
├── actions/                   AnActions utilisateur
│   ├── RepairProjectSetupAction.kt
│   ├── AttachLibrariesAction.kt
│   └── OpenRoutesAction.kt
│
├── config/                    Settings plugin (persistance, UI)
│   ├── Play1Settings.kt       PersistentStateComponent (application-level)
│   ├── Play1SettingsConfigurable.kt
│   └── Play1SettingsPanel.kt
│
├── detection/                 Logique de détection Play 1 (pur, sans IDE)
│   ├── Play1ProjectDetector.kt   détecte conf/routes, app/controllers, etc.
│   ├── Play1HomeDetector.kt      auto-detects Play Home
│   └── Play1HomeValidator.kt     valide un répertoire Play Home
│
├── model/                     Modèle interne (données Play 1)
│   ├── PlayProject.kt
│   ├── PlayRoute.kt
│   ├── PlayController.kt
│   ├── PlayAction.kt
│   ├── PlayView.kt
│   └── RepairReport.kt
│
├── project/                   Configuration projet IntelliJ (classpath, roots, run config)
│   ├── Play1LibraryManager.kt     Library API, ModifiableRootModel
│   ├── Play1SourceRootManager.kt  ContentEntry, SourceFolder
│   ├── Play1RunConfigManager.kt   RunConfigurationFactory
│   └── RepairReportDialog.kt      Affichage rapport
│
├── run/                       Run/Debug configuration IntelliJ
│   ├── Play1RunConfigurationType.kt
│   ├── Play1ApplicationConfigurationFactory.kt
│   ├── Play1ApplicationRunConfiguration.kt
│   └── Play1ApplicationRunState.kt
│
├── routes/                    Support fichier conf/routes (langage custom)
│   ├── RoutesLanguage.kt
│   ├── RoutesFileType.kt
│   ├── RoutesLexer.kt
│   ├── RoutesSyntaxHighlighter.kt
│   ├── RoutesParserDefinition.kt
│   ├── RoutesCompletionContributor.kt
│   ├── RoutesNavigationContributor.kt
│   └── RoutesAnnotator.kt
│
├── services/                  Services projet IntelliJ (état, index)
│   └── Play1ProjectService.kt     Project Service — état et index Play 1
│
├── toolwindow/                Tool Window "Play 1"
│   ├── Play1ToolWindowFactory.kt
│   ├── ProjectStatusPanel.kt
│   ├── RoutesTreePanel.kt
│   └── DiagnosticsPanel.kt
│
└── Play1StartupActivity.kt    ProjectActivity — détection à l'ouverture
```

---

## Principes d'architecture

### 1. Séparation logique métier / APIs IntelliJ

Les classes dans `detection/` et `model/` ne dépendent pas des APIs IntelliJ. Elles sont testables avec de simples tests JUnit, sans sandbox IDE.

Les classes dans `project/`, `run/`, `routes/`, `services/`, `toolwindow/` dépendent des APIs IntelliJ et sont testées avec les outils platform test (LightPlatformTestCase, BasePlatformTestCase).

### 2. Project Service comme point central

`Play1ProjectService` est le point d'entrée pour l'état d'un projet Play 1. Il maintient :
- si le projet est Play 1 (booléen + raison)
- la liste des routes parsées (cache mémoire)
- la liste des controllers/views indexés
- l'état du classpath (library attachée ou non)

Il est rafraîchi :
- À l'ouverture du projet (via `Play1StartupActivity`)
- Quand `conf/routes` est modifié (via VFS listener)
- Via le bouton "Refresh Index" de la Tool Window

### 3. Settings application-level pour Play Home

Play Home est une configuration machine, pas de projet. Il est stocké dans un `PersistentStateComponent` application-level (dans `~/.config/JetBrains/...`). Un override projet pourra être ajouté plus tard si nécessaire (voir ADR-003).

### 4. Pas de modification du code applicatif

Le plugin modifie uniquement :
- Les libraries IntelliJ (classpath)
- Les source/test roots
- Les run configurations
- Les settings du plugin

Il ne touche jamais : `conf/routes`, `conf/application.conf`, code Java, templates.

### 5. Background tasks pour les opérations longues

Les scans de JARs et les modifications de projet (library attachment) s'exécutent dans des background tasks via `ProgressManager.getInstance().runProcessWithProgressAsynchronously()` pour ne pas bloquer l'UI thread.

---

## Services IntelliJ utilisés (vue rapide)

| Service | Usage |
|---------|-------|
| `ProjectActivity` | Détection au démarrage |
| `PersistentStateComponent` | Settings Play Home |
| `Configurable` | Page Settings IDE |
| `LibraryTable` / `ModifiableRootModel` | Attacher JARs |
| `ModuleRootManager` | Configurer source roots |
| `RunConfigurationFactory` | Créer run configuration |
| `ToolWindowFactory` | Tool Window Play 1 |
| `LocalInspectionTool` | Inspections |
| `CompletionContributor` | Complétion dans conf/routes |
| `FileType` + `SyntaxHighlighter` | Support conf/routes |
| `PsiReferenceContributor` | Navigation Ctrl+Click |

Voir `docs/intellij-platform-notes.md` pour plus de détails.

---

## Flux principal : Repair Project Setup

```
User: Tools > Play 1 > Repair Project Setup
  │
  ▼
RepairProjectSetupAction.actionPerformed()
  │
  ├─ Play1ProjectDetector.isPlay1Project(project) → true/false
  ├─ Play1Settings.getInstance().playHome → configurer si absent
  ├─ Play1HomeValidator.validate(playHome) → OK/ERREUR
  │
  ├─ Play1LibraryManager.attachFrameworkLibraries(project, playHome)
  │    ├─ Scan play-*.jar dans $PLAY_HOME/framework/
  │    ├─ Scan lib/*.jar dans $PLAY_HOME/framework/lib/
  │    ├─ Scan $PROJECT_DIR/lib/*.jar
  │    └─ Création library "Play 1 Framework" + ajout au module
  │
  ├─ Play1SourceRootManager.configureRoots(project)
  │    ├─ app/ → SOURCE
  │    ├─ test/ → TEST_SOURCE
  │    └─ conf/ → RESOURCE
  │
  ├─ Play1RunConfigManager.createRunConfiguration(project)
  │    └─ "Play 1 App" (play.server.Server + classpath)
  │
  ├─ Play1ProjectService.getInstance(project).refresh()
  │
  └─ RepairReportDialog.show(report)
```

---

## Évolutions prévues (non MVP)

- **Index IntelliJ avancé** : remplacer le scan mémoire par des stubs `FileBasedIndex` pour les routes et controllers
- **Language injection** dans les templates pour les expressions `${...}`
- **Facet Play 1** : intégration optionnelle dans Project Structure
- **Services tool window** : intégration de l'app Play dans la fenêtre Services d'IntelliJ
