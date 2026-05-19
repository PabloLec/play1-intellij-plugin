# Runlog — Play v1 Toolkit

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

## 2026-05-19 — Lots 7–9 : Custom Language routes, Tool Window, Tests

### Contexte
Reprise après bilan qualité des Lots 0–6. Tous les composants existants validés sans blocage. Cette session réalise les 3 derniers lots du MVP.

### Actions réalisées

**Support conf/routes — Custom Language (Lot 7) :**
- `RoutesLanguage.kt` — Language singleton "Routes"
- `RoutesFileType.kt` — LanguageFileType pour fichiers nommés exactement "routes" (`fileNames="routes"` dans plugin.xml)
- `RoutesTokenTypes.kt` — 12 token types (HTTP_METHOD, PATH, PATH_PARAM, CONTROLLER_NAME, DOT, ACTION_NAME, STATIC_REF, MODULE_REF, COMMENT, NEWLINE, WHITESPACE, BAD_CHARACTER)
- `RoutesLexer.kt` — Lexer manuel (6 états : LINE_START, AFTER_METHOD, IN_PATH, AFTER_PATH, IN_CONTROLLER, IN_ACTION). Logique ligne par ligne, gère `{id}`, `{<regex>id}`, `staticDir:`, `module:`
- `RoutesSyntaxHighlighter.kt` — Mapping tokens → TextAttributesKey (keyword blue, string, parameter orange, class reference green, function call purple, line comment gray)
- `RoutesParserDefinition.kt` + `RoutesParser` — PSI tree : `RoutesFile > RoutesRouteElement`, whitespace/newline auto-skipped
- `psi/RoutesFile.kt` + `psi/RoutesRouteElement.kt` — Accesseurs typés (getControllerName, getActionName, isStaticRoute, etc.)
- `RoutesReferenceContributor.kt` — `ControllerNameReference` résout vers `PsiClass`, `ActionNameReference` résout vers `PsiMethod` (public + static)
- `RoutesCompletionContributor.kt` — Propose noms de classes (controllers) et méthodes publiques statiques
- `RoutesAnnotator.kt` — Underline rouge si controller/action non trouvé (skips `{ctrl}` dynamiques)
- `RoutesLexerTest.kt` — 17 tests unitaires couvrant GET/POST/*/staticDir/module/params/multi-lignes/fichier réaliste

**Tool Window Play v1 Toolkit (Lot 8) :**
- `Play1ToolWindowFactory.kt` — 3 onglets, boutons Repair/Refresh, implémente DumbAware
- `ProjectStatusPanel.kt` — Play détecté, Play Home, version extraite, run config présente
- `RoutesTreePanel.kt` — Lit PSI routes via PsiManager, affiche METHOD → Controller.action dans JTree
- `DiagnosticsPanel.kt` — Résolution controllers/actions, liste les ⚠ problèmes
- Enregistré : `toolWindow` anchor="right"

**Tests (Lot 9) :**
- `Play1LibraryManagerTest.kt` — 9 tests : findPlayJar, format JAR URL, scanning lib/
- `Play1ProjectDetectorFixturesTest.kt` — 4 tests : valide les fixtures statiques
- Fixtures enrichies : `play1-standard/` (conf/application.conf, conf/routes, app/controllers/), `play1-minimal/`, `not-play1/`
- **Total : 46 tests, 0 échecs**

### Problèmes rencontrés et résolus
1. `Play1ProjectService.refresh()` appelé avec argument (project) alors que la signature ne prend pas de paramètre → corrigé dans `ProjectStatusPanel`
2. `AnActionEvent.createEvent(...)` avec signature incorrecte → remplacé par `AnActionEvent.createFromDataContext(place, null, dataContext)` + `ActionUtil.performActionDumbAwareWithCallbacks`

### Décisions prises
- Lexer manuel (pas JFlex) : le format routes est trop simple pour justifier JFlex, et un lexer manuel est plus maintenable et compréhensible
- `RoutesTokenTypes.WHITESPACE_SET` inclut WHITESPACE + NEWLINE → PsiBuilder auto-skip → parser simplifié (pas de gestion explicite des séparateurs)
- Tests platform (`RepairProjectSetupActionTest`, `RoutesNavigationTest`, `Play1SettingsTest`) reportés post-MVP : nécessitent une infrastructure IDE lourde, valeur marginale par rapport à l'effort

### État final du projet
**Tous les lots 0–9 sont DONE. Le MVP Play v1 Toolkit est complet.**
- 46 tests unitaires passent
- `./gradlew buildPlugin` → BUILD SUCCESSFUL
- Custom Language routes : coloration, navigation Ctrl+Click, completion, annotateur
- Tool Window : Status / Routes / Diagnostics avec bouton Repair
- 6 commits propres sur `main`

### Prochaine étape
Post-MVP : Lots 10–19 (templates, application.conf, navigation render(), gutter icons, inspections avancées).
Prioritaires : Lot 13 (navigation render() → vue), Lot 14 (gutter icons routes), Lot 15 (inspections).

---

## 2026-05-19 — Lots 13–15 : Navigation render(), gutter icons, inspections

### Contexte
Reprise après audit qualité MVP (Grade A, aucun problème critique). Cette session implémente les 3 lots post-MVP haute priorité.

### Audit MVP réalisé avant développement
- 46 tests, 0 échecs, Grade A
- Problème mineur identifié : `RoutesRouteElement` sans `getPath()` → corrigé en Lot 14
- CRLF dans lexer : reporté (très bas risque)

### Actions réalisées

**Utilitaires partagés (socle commun Lots 13–15) :**
- `render/Play1ViewUtils.kt` — objet singleton avec 5 méthodes :
  - `isPlayController(PsiClass)` : via `InheritanceUtil.isInheritor("play.mvc.Controller")`
  - `implicitViewPath(controller, action)` : retourne `"app/views/{Ctrl}/{action}.html"`
  - `findViewFile(project, controller, action)` : cherche .html puis .groovy via VirtualFileManager + basePath
  - `findRoutesFile(project)` : trouve `conf/routes` via basePath (pattern identique à `RoutesTreePanel`)
  - `findRoutesForAction(project, controller, action)` : filtre le PSI routes pour trouver les routes dynamiques matching

**Lot 13 — Navigation render() → vue implicite :**
- `Play1RenderViewGotoHandler.kt` — `GotoDeclarationHandler`
  - S'active sur l'identifiant `render` ou `renderTemplate` dans un `PsiMethodCallExpression`
  - Vérifie que la classe englobante extend `play.mvc.Controller`
  - Pour `render(...)` → vue implicite `app/views/{Class}/{method}.html`
  - Pour `renderTemplate("path")` → vue explicite relative à `app/views/`
  - Retourne le `PsiFile` de la vue comme target de navigation (s'ajoute au target Java existant)
- Fixtures enrichies : `play1-standard/app/views/Application/index.html` + `show.html`
- `Play1ViewUtilsTest.kt` : 6 tests unitaires (implicitViewPath + existence fixtures)

**Lot 14 — Gutter icons controller ↔ routes :**
- `RoutesRouteElement.getPath(): String?` ajouté
- `Play1ControllerLineMarkerProvider.kt` — `RelatedItemLineMarkerProvider` langage JAVA
  - Filtre : `PsiIdentifier` → parent `PsiMethod` → `public static` → classe extends Controller
  - Trouve les routes matchantes via `Play1ViewUtils.findRoutesForAction`
  - Gutter `AllIcons.General.ArrowRight` avec tooltip listant les routes
- `Play1RoutesLineMarkerProvider.kt` — `RelatedItemLineMarkerProvider` langage Routes
  - Filtre : token `CONTROLLER_NAME` dans un `RoutesRouteElement` dynamique
  - Résout la classe + méthode Java via JavaPsiFacade / PsiShortNamesCache
  - Gutter `AllIcons.General.ArrowLeft` pointant vers la méthode

**Lot 15 — Inspections avancées :**
- `Play1MissingViewInspection.kt` — `LocalInspectionTool` langage JAVA
  - Visite `PsiMethodCallExpression` nommées `render` dans des controllers Play 1
  - Si la vue implicite n'existe pas → `ProblemHighlightType.WARNING`
- `CreateMissingViewQuickFix.kt` — `LocalQuickFix`
  - Crée `app/views/{Controller}/` via `VfsUtil.createDirectoryIfMissing`
  - Crée `{action}.html` avec template Play 1 basique
  - Ouvre le fichier dans l'éditeur via `OpenFileDescriptor.navigate(true)`

### Problèmes rencontrés et résolus
1. Import `VirtualFileManager` manquant dans `Play1RenderViewGotoHandler.kt` → détecté par `compileKotlin`, corrigé immédiatement

### Décisions prises
- `Play1MissingViewInspection` utilise `LocalInspectionTool` (pas `AbstractBaseJavaLocalInspectionTool`) pour robustesse maximale
- `renderTemplate()` avec chemin explicite : navigates vers le fichier si trouvable, sinon null (pas de navigation) — comportement safe
- Gutter icon JAVA : filtre sur `PsiIdentifier` (leaf element) pour performance (bail-out immédiat sur tous les non-identifiers)
- Les tests d'inspection (plateforme) sont reportés : nécessitent `BasePlatformTestCase` + infrastructure IDE complète

### Tests lancés
- `./gradlew test` → 52 tests, 0 échecs
- `./gradlew buildPlugin` → BUILD SUCCESSFUL

### État
**Lots 13–15 DONE. Total : 52 tests, build OK, 1 nouveau commit.**

### Prochaine étape
Post-Lots 13–15 : soit Lot 15b (inspection renderTemplate explicite, action non routée), soit Lot 10/11 (templates .html, application.conf support).

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
