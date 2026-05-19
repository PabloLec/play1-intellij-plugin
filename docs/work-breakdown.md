# Work Breakdown — Play 1 Toolkit

> Découpage de tout le travail en lots cohérents et progressifs.
> Ce fichier doit être mis à jour à chaque avancée de lot.
> Statuts : `TODO` · `IN PROGRESS` · `DONE` · `BLOCKED`

---

## Lot 0 — Analyse initiale et documentation

**Statut :** `DONE`

**Objectif :** Analyser le besoin, structurer la documentation de travail, formaliser les premières décisions techniques.

**Tâches :**
- [x] Lire `expression-de-besoin.md` intégralement
- [x] Explorer l'environnement (samples `/tmp/play1-master`, structure Play 1)
- [x] Formaliser les ADRs initiaux (5 décisions structurantes)
- [x] Créer toute la documentation de travail (`docs/`)

**Critères d'acceptation :**
- Tous les fichiers `docs/` créés et remplis
- ADRs écrits et indexés
- `todo.md` reflète l'ensemble du plan

**Fichiers concernés :**
```
docs/todo.md
docs/runlog.md
docs/work-breakdown.md
docs/architecture.md
docs/testing.md
docs/intellij-platform-notes.md
docs/play1-assumptions.md
docs/adr/*.md
```

---

## Lot 1 — Bootstrap projet IntelliJ plugin moderne

**Statut :** `DONE`

**Objectif :** Créer un projet Gradle/Kotlin buildable avec l'IntelliJ Platform Gradle Plugin 2.x. Le plugin doit se lancer dans une sandbox IntelliJ.

**Tâches :**
- [ ] `build.gradle.kts` avec `org.jetbrains.intellij.platform` 2.x
- [ ] `settings.gradle.kts`
- [ ] `gradle.properties` (versions, group, pluginId)
- [ ] Gradle wrapper (7.x ou 8.x)
- [ ] `plugin.xml` minimal (id, name, vendor, depends java + platform)
- [ ] Structure packages Kotlin (`actions/`, `config/`, `detection/`, etc.)
- [ ] `README.md` initial
- [ ] `CHANGELOG.md`
- [ ] `.gitignore`
- [ ] `.github/workflows/build.yml` (CI : build + verifyPlugin)

**Critères d'acceptation :**
- `./gradlew buildPlugin` → BUILD SUCCESSFUL
- `./gradlew runIde` → IntelliJ sandbox s'ouvre
- `./gradlew verifyPlugin` → pas d'erreur bloquante
- CI GitHub Actions verte sur push

**Fichiers concernés :**
```
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/wrapper/
src/main/kotlin/com/github/pablolec/play1toolkit/
src/main/resources/META-INF/plugin.xml
.github/workflows/build.yml
README.md
CHANGELOG.md
.gitignore
```

---

## Lot 2 — Détection projet Play 1

**Statut :** `DONE`

**Objectif :** Détecter automatiquement à l'ouverture d'un projet qu'il s'agit d'un projet Play 1, et afficher une notification.

**Tâches :**
- [ ] `Play1ProjectDetector.kt` — logique pure sans dépendances IDE
- [ ] `Play1ProjectService.kt` — Project Service (cache état Play 1)
- [ ] `Play1StartupActivity.kt` — ProjectActivity déclenchée à l'ouverture
- [ ] Enregistrement dans `plugin.xml`
- [ ] Fixtures de test : layouts Play 1 valides et invalides
- [ ] `Play1ProjectDetectorTest.kt` — tests unitaires purs

**Critères d'acceptation :**
- Ouvrir `sample-play1-app/` → notification "Play 1 project detected"
- Ouvrir un projet Spring Boot → aucune notification
- Tests unitaires passent sans démarrer d'IDE

**Logique de détection :**
- Critères forts : `conf/application.conf` + `conf/routes` + `app/controllers/`
- 2/3 critères forts suffisent pour déclencher la détection
- Critères complémentaires : `conf/dependencies.yml`, `app/views/`

**Fichiers concernés :**
```
src/main/kotlin/.../detection/Play1ProjectDetector.kt
src/main/kotlin/.../services/Play1ProjectService.kt
src/main/kotlin/.../Play1StartupActivity.kt
src/test/kotlin/.../detection/Play1ProjectDetectorTest.kt
src/test/resources/fixtures/play1-standard/
src/test/resources/fixtures/play1-minimal/
src/test/resources/fixtures/not-play1/
```

---

## Lot 3 — Settings Play Home

**Statut :** `DONE`

**Objectif :** Permettre à l'utilisateur de configurer son installation Play 1 (Play Home) via une page Settings dédiée.

**Tâches :**
- [ ] `Play1Settings.kt` — PersistentStateComponent application-level
- [ ] `Play1SettingsConfigurable.kt` — intégration Settings > Tools > Play 1 Toolkit
- [ ] `Play1SettingsPanel.kt` — UI (champ Play Home, boutons Browse/Auto-detect, validation)
- [ ] `Play1HomeDetector.kt` — auto-detection (PLAY_HOME env, chemins courants, which play)
- [ ] `Play1HomeValidator.kt` — validation (présence JAR principal, play/mvc/Controller.class)
- [ ] Enregistrement dans `plugin.xml`

**Critères d'acceptation :**
- Settings > Tools > Play 1 Toolkit accessible
- Bouton "Auto-detect" trouve Play Home si `PLAY_HOME` défini ou installé dans `/opt/play-1.*`
- Validation affiche la version Play détectée ou un message d'erreur clair
- Settings persistants entre redémarrages IDE

**Champs settings (MVP) :**
```
Play Home           [/opt/play-1.2.7]  [Browse...]  [Auto-detect]
                    Validation : "Play 1.2.7 — OK" / "Erreur : ..."
Default Play ID     [dev]
Default HTTP port   [9000]
Default debug port  [5005]
```

**Fichiers concernés :**
```
src/main/kotlin/.../config/Play1Settings.kt
src/main/kotlin/.../config/Play1SettingsConfigurable.kt
src/main/kotlin/.../config/Play1SettingsPanel.kt
src/main/kotlin/.../detection/Play1HomeDetector.kt
src/main/kotlin/.../detection/Play1HomeValidator.kt
```

---

## Lot 4 — Repair Project Setup

**Statut :** `DONE`

**Objectif :** Implémenter l'action centrale du plugin : réparer automatiquement la configuration IntelliJ d'un projet Play 1.

**Tâches :**
- [ ] `RepairProjectSetupAction.kt` — AnAction principale
- [ ] `Play1LibraryManager.kt` — création/mise à jour library "Play 1 Framework"
- [ ] `Play1SourceRootManager.kt` — configuration app/ + test/ + conf/
- [ ] `Play1RunConfigManager.kt` — création run configuration "Play 1 App"
- [ ] `RepairReport.kt` — modèle de données du rapport
- [ ] `RepairReportDialog.kt` — affichage rapport (dialog ou notification)
- [ ] Enregistrement dans `plugin.xml` (menu Tools > Play 1)
- [ ] Tests plateforme `RepairProjectSetupActionTest`

**Étapes de l'action :**
1. Détecter la racine Play 1
2. Vérifier/demander Play Home
3. Localiser `play-*.jar` dans `$PLAY_HOME/framework/`
4. Créer library IntelliJ "Play 1 Framework" (play.jar + lib/*.jar)
5. Attacher sources si `$PLAY_HOME/framework/src/` présent
6. Attacher `$PROJECT_DIR/lib/*.jar`
7. Marquer `app/` → source root
8. Marquer `test/` → test source root
9. Marquer `conf/` → resources root
10. Créer run configuration "Play 1 App"
11. Afficher rapport

**Critères d'acceptation :**
- Après exécution : `import play.mvc.*;` résolu (plus en rouge)
- `app/` marqué source root dans Project Structure
- Run configuration "Play 1 App" créée
- Rapport affiche tous les éléments avec statut OK/ERREUR

**Fichiers concernés :**
```
src/main/kotlin/.../actions/RepairProjectSetupAction.kt
src/main/kotlin/.../project/Play1LibraryManager.kt
src/main/kotlin/.../project/Play1SourceRootManager.kt
src/main/kotlin/.../project/Play1RunConfigManager.kt
src/main/kotlin/.../project/RepairReport.kt
src/main/kotlin/.../project/RepairReportDialog.kt
src/test/kotlin/.../actions/RepairProjectSetupActionTest.kt
```

---

## Lot 5 — Run/Debug configuration

**Statut :** `DONE`

**Objectif :** Permettre de lancer et debugger l'application Play 1 directement depuis IntelliJ sans connaître les détails techniques.

**Tâches :**
- [ ] `Play1RunConfigurationType.kt`
- [ ] `Play1ApplicationRunConfiguration.kt`
- [ ] `Play1ApplicationConfigurationFactory.kt`
- [ ] `Play1ApplicationRunState.kt` — lancement JVM avec `play.server.Server`
- [ ] `Play1CommandRunConfiguration.kt` — optionnel, mode CLI `play run`
- [ ] Enregistrement dans `plugin.xml`
- [ ] Test manuel dans sandbox IDE

**Configuration de lancement :**
```
Main class: play.server.Server
VM options:
  -Dapplication.path=$PROJECT_DIR$
  -Dplay.id=dev
  (debug) -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
Classpath:
  play-*.jar + framework/lib/*.jar + lib/*.jar + app/
```

**Critères d'acceptation :**
- Run ▶ "Play 1 App" démarre l'application sur port 9000
- Debug 🐛 permet de poser des breakpoints
- Console Play visible dans IntelliJ

**Fichiers concernés :**
```
src/main/kotlin/.../run/Play1RunConfigurationType.kt
src/main/kotlin/.../run/Play1ApplicationRunConfiguration.kt
src/main/kotlin/.../run/Play1ApplicationConfigurationFactory.kt
src/main/kotlin/.../run/Play1ApplicationRunState.kt
```

---

## Lot 6 — Sample Play 1 app + fixtures de test

**Statut :** `DONE`

**Objectif :** Intégrer un projet Play 1 réaliste dans le repository pour les tests manuels et automatisés.

**Tâches :**
- [ ] Copier/adapter `yabe` depuis `/tmp/play1-master/samples-and-tests/yabe/`
- [ ] Nettoyer (supprimer les fichiers non nécessaires : .idea/, fichiers de build)
- [ ] Créer `play-stub.jar` avec classes minimales Play
- [ ] Placer dans `src/test/resources/stubs/`
- [ ] Documenter dans `docs/testing.md`

**Critères d'acceptation :**
- `sample-play1-app/` contient un projet Play 1 valide détectable par le plugin
- `play-stub.jar` contient `play.Play`, `play.mvc.Controller`, `play.server.Server`
- Les tests unitaires peuvent utiliser le stub sans Play Home réel

**Fichiers concernés :**
```
sample-play1-app/
src/test/resources/stubs/play-stub.jar
```

---

## Lot 7 — Support basique conf/routes

**Statut :** `DONE`

**Objectif :** Rendre `conf/routes` intelligent : coloration syntaxique, navigation Ctrl+Click vers les controllers Java.

**Tâches :**
- [ ] `RoutesLanguage.kt` + `RoutesFileType.kt`
- [ ] `RoutesLexer.kt` (tokenizer manuel ou JFlex)
- [ ] `RoutesSyntaxHighlighter.kt`
- [ ] `RoutesParserDefinition.kt` (PSI basique)
- [ ] `RoutesCompletionContributor.kt` (controllers + actions)
- [ ] `RoutesNavigationContributor.kt` (Ctrl+Click → méthode Java)
- [ ] `RoutesAnnotator.kt` (controller/action inconnu)
- [ ] Enregistrement dans `plugin.xml`
- [ ] Tests `RoutesParserTest`, `RoutesNavigationTest`

**Format routes Play 1 :**
```
GET     /login              Security.login
POST    /login              Security.authenticate
GET     /patients/{id}      Patients.show
*       /{controller}/{action}  {controller}.{action}
GET     /public/            staticDir:public
*       /admin              module:crud
```

**Critères d'acceptation :**
- `conf/routes` s'ouvre avec coloration syntaxique
- Ctrl+Click sur `Patients.show` → ouvre la méthode Java
- Controller inexistant souligné en rouge

**Fichiers concernés :**
```
src/main/kotlin/.../routes/RoutesLanguage.kt
src/main/kotlin/.../routes/RoutesFileType.kt
src/main/kotlin/.../routes/RoutesLexer.kt
src/main/kotlin/.../routes/RoutesSyntaxHighlighter.kt
src/main/kotlin/.../routes/RoutesParserDefinition.kt
src/main/kotlin/.../routes/RoutesCompletionContributor.kt
src/main/kotlin/.../routes/RoutesNavigationContributor.kt
src/main/kotlin/.../routes/RoutesAnnotator.kt
src/test/kotlin/.../routes/RoutesParserTest.kt
src/test/kotlin/.../routes/RoutesNavigationTest.kt
```

---

## Lot 8 — Tool Window Play 1

**Statut :** `DONE`

**Objectif :** Ajouter un panneau latéral "Play 1" affichant l'état du projet, les routes, les diagnostics et des actions rapides.

**Tâches :**
- [ ] `Play1ToolWindowFactory.kt`
- [ ] `ProjectStatusPanel.kt`
- [ ] `RoutesTreePanel.kt`
- [ ] `DiagnosticsPanel.kt`
- [ ] Enregistrement dans `plugin.xml`
- [ ] Test manuel dans sandbox

**Critères d'acceptation :**
- Tool Window "Play 1" visible dans la barre latérale
- Affiche statut (Play détecté, Play Home, classpath, run config)
- Liste les routes parsées
- Bouton "Repair" déclenche l'action de réparation

**Fichiers concernés :**
```
src/main/kotlin/.../toolwindow/Play1ToolWindowFactory.kt
src/main/kotlin/.../toolwindow/ProjectStatusPanel.kt
src/main/kotlin/.../toolwindow/RoutesTreePanel.kt
src/main/kotlin/.../toolwindow/DiagnosticsPanel.kt
```

---

## Lot 9 — Tests complets

**Statut :** `DONE`

**Objectif :** Couvrir les composants critiques par des tests automatisés.

**Tâches :**
- [ ] `Play1ProjectDetectorTest` (unitaire — layouts variés)
- [ ] `RoutesParserTest` (unitaire — parsing)
- [ ] `Play1LibraryManagerTest` (unitaire — scan JARs)
- [ ] `RepairReportTest` (unitaire — construction rapport)
- [ ] `RepairProjectSetupActionTest` (plateforme)
- [ ] `RoutesNavigationTest` (plateforme)
- [ ] `Play1SettingsTest` (plateforme — persistance)

**Critères d'acceptation :**
- `./gradlew test` → tous les tests unitaires passent
- `./gradlew verifyPlugin` → pas d'erreur de compatibilité

**Fichiers concernés :**
```
src/test/kotlin/.../detection/Play1ProjectDetectorTest.kt
src/test/kotlin/.../routes/RoutesParserTest.kt
src/test/kotlin/.../project/Play1LibraryManagerTest.kt
src/test/kotlin/.../project/RepairReportTest.kt
src/test/kotlin/.../actions/RepairProjectSetupActionTest.kt
src/test/kotlin/.../routes/RoutesNavigationTest.kt
src/test/kotlin/.../config/Play1SettingsTest.kt
```

---

## Lots futurs (post-MVP)

| Lot | Sujet | Priorité |
|-----|-------|----------|
| 10 | Support templates Play 1 (`.html`) | Moyenne |
| 11 | Support `application.conf` (complétion, documentation clés) | Moyenne |
| 12 | Support `dependencies.yml` + action `play deps` | Basse |
| 13 | Navigation `render()` → vue implicite | Haute |
| 14 | Gutter icons controller → routes | Haute |
| 15 | Inspections avancées (vue manquante, route non résolue) | Haute |
| 16 | Route Runner / HTTP Client intégration | Basse |
| 17 | Services tool window intégration | Basse |
| 18 | Onboarding mode | Basse |
| 19 | Export Markdown overview | Basse |
