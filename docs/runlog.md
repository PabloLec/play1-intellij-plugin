# Runlog — Play 1 Toolkit

> Journal chronologique d'exécution. Toute personne (développeur ou agent IA) reprenant
> ce projet doit pouvoir lire ce fichier et comprendre ce qui a été fait, pourquoi,
> et ce qui reste à faire sans relire tout l'historique Git.

---

## 2026-05-19 — Lot 0 : Analyse initiale et documentation

### Contexte
Premier démarrage du projet. Le repository ne contenait que `expression-de-besoin.md`.

### Actions réalisées

**Lecture du besoin :**
- Lu intégralement `expression-de-besoin.md` (1760 lignes)
- Le document décrit un plugin IntelliJ IDEA Ultimate pour Play Framework 1.x
- Objectif principal : supprimer la friction à l'ouverture d'un vieux projet Play 1 (imports rouges, pas de run config, pas de source roots)
- Action centrale identifiée : **Repair Play 1 Project Setup**
- Roadmap officielle en 6 phases (Foundation → Run/Debug → Routes → Controllers/Views → Tool Window → Inspections avancées)

**Exploration de l'environnement :**
- Samples Play 1 disponibles localement dans `/tmp/play1-master/samples-and-tests/` (17 projets, dont `yabe`)
- Framework Play 1 en source non compilée dans `/tmp/play1-master/framework/src/`
- Constat important : `/tmp/play1-master` contient des **sources non compilées**. Un vrai Play Home installé (ex: `/opt/play-1.2.7/`) contient `framework/play-1.2.7.jar` compilé. Le plugin doit cibler ce JAR.
- 86 JARs de dépendances dans `framework/lib/`
- Sample `yabe` : blog complet avec 7 controllers, 4 models, vues, routes, conf/

**Décisions prises :**
- ADR-001 : IntelliJ Platform Gradle Plugin 2.x (org.jetbrains.intellij.platform)
- ADR-002 : Kotlin uniquement
- ADR-003 : Settings Play Home au niveau application (machine), override projet possible
- ADR-004 : Custom FileType pour `conf/routes` (pas Language Injection)
- ADR-005 : Project Service avec cache mémoire pour l'index interne

**Documentation créée :**
- `docs/todo.md` — liste vivante des tâches
- `docs/runlog.md` — ce fichier
- `docs/work-breakdown.md` — découpage lots
- `docs/architecture.md` — architecture cible
- `docs/testing.md` — stratégie de test
- `docs/intellij-platform-notes.md` — notes APIs IntelliJ
- `docs/play1-assumptions.md` — hypothèses Play 1
- `docs/adr/` — 5 ADRs initiaux

### Prochaine étape
Lot 7 : Support conf/routes (Custom Language, Lexer, Highlighter, Navigation).

---

## 2026-05-19 — Lots 1–6 : Bootstrap complet + MVP fonctionnel

### Contexte
Tous les lots 1 à 6 ont été réalisés en une seule session. Le MVP core est implémenté.

### Actions réalisées

**Bootstrap projet (Lot 1) :**
- Gradle 8.8 + IntelliJ Platform Gradle Plugin 2.1.0 (cible IntelliJ IU 2024.1)
- `build.gradle.kts` avec `libs.versions.toml`
- CI GitHub Actions `.github/workflows/build.yml`
- `./gradlew buildPlugin` → BUILD SUCCESSFUL
- `./gradlew test` → 16 tests passent

**Détection Play 1 (Lot 2) :**
- `Play1ProjectDetector.kt` — logique pure (path-based, pas d'API IntelliJ)
- `Play1ProjectService.kt` — Project Service IntelliJ
- `Play1StartupActivity.kt` — notification à l'ouverture du projet

**Settings Play Home (Lot 3) :**
- `Play1Settings.kt` — PersistentStateComponent application-level
- `Play1SettingsConfigurable.kt` + `Play1SettingsPanel.kt` — UI Kotlin DSL
- `Play1HomeDetector.kt` — auto-detect via PLAY_HOME, chemins courants, `which play`
- `Play1HomeValidator.kt` — validation par scan du JAR et présence de Controller.class

**Repair Project Setup (Lot 4) :**
- `RepairProjectSetupAction.kt` — AnAction avec ProgressIndicator background
- `Play1LibraryManager.kt` — création library "Play 1 Framework" via LibraryTable + ModifiableRootModel
- `Play1SourceRootManager.kt` — configuration app/ (SOURCE), test/ (TEST_SOURCE), conf/ (RESOURCE)
- `Play1RunConfigManager.kt` — création run config via ConfigurationTypeUtil
- `RepairReport.kt` — modèle rapport typé (OK/ERROR/SKIPPED)

**Run/Debug (Lot 5) :**
- `Play1RunConfigurationType.kt` + Factory + Configuration + Editor + RunState
- Lancement via `play.server.Server` avec `-Dapplication.path`, `-Dplay.id`
- Debug via injection JDWP automatique

**Sample app + fixtures (Lot 6) :**
- `sample-play1-app/` — copie adaptée de `yabe` (controllers, models, views, conf)
- `src/test/resources/stubs/play-stub.jar` — JAR minimal avec play.Play, play.mvc.Controller, play.server.Server

### Tests
- 16 tests unitaires passent
- `Play1ProjectDetectorTest` : 6 cas (standard, 2 critères, 1 critère, vide, Spring Boot, companion)
- `Play1HomeValidatorTest` : 5 cas (inexistant, sans framework/, sans JAR, avec stub 1.0.0, version 1.2.7)
- `RepairReportTest` : 5 cas (OK, erreur, texte, statut ERRORS, skipped)

### Problèmes rencontrés et résolus
1. `Play1SettingsPanel.kt` — erreur DSL Kotlin UI (`.component` private dans DialogPanel) → résolu en utilisant des champs séparés (`TextFieldWithBrowseButton`, `JBTextField`) dans un `panel { }` sans appel `.component`
2. `Play1RunConfigurationType` — ne peut pas utiliser `service()` (c'est un `ConfigurationType` pas un Service) → résolu en utilisant `ConfigurationTypeUtil.findConfigurationType()`
3. `ProjectActivity` dans `plugin.xml` — la registration via `<projectListeners topic="...">` est incorrecte → résolu avec `<postStartupActivity implementation="..."/>`

### Décisions prises
- La page Settings utilise le Kotlin UI DSL avec des composants Swing pré-instanciés (meilleure compatibilité)
- `RepairReport.toText()` retourne une chaîne formatée (dialog simple) — pas de fenêtre dédiée dans le MVP
- Le run configuration Editor est intentionnellement simple (extension future possible)

### Prochaine étape
Lot 7 : Support `conf/routes` — Custom Language (RoutesLanguage, RoutesFileType, Lexer, SyntaxHighlighter, CompletionContributor, NavigationContributor).

---

_Template pour les prochaines entrées :_

```
## YYYY-MM-DD — Lot N : Titre

### Contexte
...

### Actions réalisées
- ...

### Problèmes rencontrés
- ...

### Décisions prises
- ...

### Tests lancés
- ...

### Prochaine étape
...
```
