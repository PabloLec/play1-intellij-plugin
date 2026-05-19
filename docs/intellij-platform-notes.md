# IntelliJ Platform Notes — Play v1 Toolkit

> Ce document décrit les APIs IntelliJ Platform utilisées dans le plugin,
> pourquoi elles sont utilisées, et les points d'attention associés.

---

## ProjectActivity (ex StartupActivity)

**Utilisé pour :** `Play1StartupActivity.kt`

**Pourquoi :** Permet d'exécuter du code au démarrage d'un projet IntelliJ. Remplace `StartupActivity` qui est déprécié depuis IntelliJ 2023.x. `ProjectActivity` est l'interface moderne (suspend fun).

**Enregistrement dans plugin.xml :**
```xml
<projectListeners>
    <listener class="...Play1StartupActivity"
              topic="com.intellij.openapi.startup.ProjectActivity"/>
</projectListeners>
```

**Point d'attention :** S'exécute sur le coroutine dispatcher d'IntelliJ, pas sur l'EDT. Mettre les opérations UI (notifications) dans `com.intellij.openapi.application.invokeLater`.

---

## PersistentStateComponent

**Utilisé pour :** `Play1Settings.kt`

**Pourquoi :** Mécanisme standard IntelliJ pour persister l'état d'un service entre sessions IDE. Sérialisé en XML dans le répertoire de configuration.

**Deux niveaux :**
- `@Service(Service.Level.APP)` → settings machine (Play Home) dans `~/.config/JetBrains/...`
- `@Service(Service.Level.PROJECT)` → settings projet dans `.idea/`

Play Home est application-level (ADR-003). Un override projet pourra être ajouté plus tard.

**Enregistrement dans plugin.xml :**
```xml
<applicationService serviceImplementation="...Play1Settings"/>
```

---

## Configurable

**Utilisé pour :** `Play1SettingsConfigurable.kt`

**Pourquoi :** API standard pour intégrer une page de settings dans `File > Settings > Tools > Play v1 Toolkit`.

**Enregistrement dans plugin.xml :**
```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable
        parentId="tools"
        id="play1toolkit.settings"
        displayName="Play v1 Toolkit"
        instance="...Play1SettingsConfigurable"/>
</extensions>
```

---

## Project Model API — Library + ModifiableRootModel

**Utilisé pour :** `Play1LibraryManager.kt`, `Play1SourceRootManager.kt`

**Pourquoi :** Permet de manipuler programmatiquement le classpath d'un module IntelliJ — ajouter des JARs, configurer les source roots, créer des dépendances de library.

**Pattern obligatoire :**
```kotlin
WriteCommandAction.runWriteCommandAction(project) {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    try {
        // modifications...
        model.commit()
    } catch (e: Exception) {
        model.dispose()
        throw e
    }
}
```

**Types de source roots :**
- `JavaSourceRootType.SOURCE` → `app/`
- `JavaSourceRootType.TEST_SOURCE` → `test/`
- `JavaResourceRootType.RESOURCE` → `conf/`

**Création d'une library :**
```kotlin
val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
val library = libraryTable.modifiableModel.createLibrary("Play 1 Framework")
val libraryModel = library.modifiableModel
libraryModel.addRoot(VirtualFileManager.constructUrl("jar", jar.path + "!/"), OrderRootType.CLASSES)
libraryModel.commit()
```

**Point d'attention :** Toutes les modifications doivent se faire dans un write action (EDT ou write-safe context). Utiliser `ApplicationManager.getApplication().runWriteAction { }`.

---

## RunConfigurationType + ConfigurationFactory

**Utilisé pour :** `Play1RunConfigurationType.kt`, `Play1ApplicationRunConfiguration.kt`

**Pourquoi :** Permet de créer un type de run configuration personnalisé visible dans "Edit Configurations..." et "Run > Run...".

**Pattern :**
1. `RunConfigurationType` — définit l'icône, le nom, l'id unique
2. `ConfigurationFactory` — factory pour créer des instances
3. `RunConfiguration` — l'instance de configuration avec les paramètres
4. `RunProfileState` — définit comment lancer le process

**Enregistrement dans plugin.xml :**
```xml
<runConfigurationProducer implementation="...Play1RunConfigurationProducer"/>
<configurationType implementation="...Play1RunConfigurationType"/>
```

**Lancement JVM via `JavaCommandLineState` :**
```kotlin
class Play1ApplicationRunState(environment: ExecutionEnvironment, config: Play1ApplicationRunConfiguration)
    : JavaCommandLineState(environment) {
    override fun createJavaParameters(): JavaParameters {
        val params = JavaParameters()
        params.mainClass = "play.server.Server"
        params.vmParametersList.add("-Dapplication.path=${config.applicationPath}")
        // ...
        return params
    }
}
```

---

## ToolWindowFactory

**Utilisé pour :** `Play1ToolWindowFactory.kt`

**Pourquoi :** Permet d'ajouter un panneau latéral "Play v1 Toolkit" dans l'IDE.

**Enregistrement dans plugin.xml :**
```xml
<toolWindow id="Play v1 Toolkit"
            anchor="right"
            factoryClass="...Play1ToolWindowFactory"
            icon="/icons/play1.svg"/>
```

**Point d'attention :** Utiliser le Kotlin UI DSL (`com.intellij.ui.dsl.builder`) pour construire l'UI — plus moderne que le Swing brut.

---

## FileType + SyntaxHighlighter (Custom Language)

**Utilisé pour :** `RoutesFileType.kt`, `RoutesSyntaxHighlighter.kt`

**Pourquoi :** Permet d'associer les fichiers nommés `routes` (sans extension) à notre langage custom et d'appliquer la coloration syntaxique.

**Détection du fichier :** Par nom exact (`routes`) plutôt que par extension.

**Enregistrement dans plugin.xml :**
```xml
<fileType name="Play Routes"
          language="PlayRoutes"
          implementationClass="...RoutesFileType"
          fileNames="routes"/>
```

**Point d'attention :** Le fichier `conf/routes` n'a pas d'extension. Il faut utiliser `fileNames` (pas `extensions`) dans la déclaration.

---

## CompletionContributor

**Utilisé pour :** `RoutesCompletionContributor.kt`

**Pourquoi :** Fournir l'autocomplétion des controllers et actions dans `conf/routes`.

**Approche :** Scanner les classes Java héritant de `play.mvc.Controller` dans le projet (via `PsiShortNamesCache` ou `ClassInheritorsSearch`) et proposer leurs méthodes publiques statiques.

---

## PsiReferenceContributor

**Utilisé pour :** `RoutesNavigationContributor.kt`

**Pourquoi :** Permet de rendre les références dans `conf/routes` navigables avec Ctrl+Click. La référence `Patients.show` dans routes pointe vers la méthode Java correspondante.

---

## LocalInspectionTool

**Utilisé pour :** futures inspections (route sans controller, vue manquante)

**Pourquoi :** Permet d'afficher des warnings/erreurs dans l'éditeur, avec des quick fixes.

**Note :** À implémenter après le MVP routes.

---

## LineMarkerProvider

**Utilisé pour :** futur — gutter icons sur les méthodes controller (routes associées)

**Pourquoi :** Affiche une icône dans la gouttière d'un fichier Java, à côté d'une méthode controller, indiquant les routes qui la ciblent.

---

## Plugin Verifier

**Commande :** `./gradlew verifyPlugin`

**Pourquoi :** Vérifie la compatibilité binaire du plugin avec les versions IntelliJ ciblées. Détecte l'usage d'APIs internes ou obsolètes.

**Configuration dans `build.gradle.kts` :**
```kotlin
intellijPlatform {
    pluginVerification {
        ides {
            recommended()
        }
    }
}
```

---

## Règles à respecter

- Éviter les APIs annotées `@ApiStatus.Internal` — utiliser uniquement les APIs publiques
- Ne jamais bloquer l'EDT (Event Dispatch Thread) avec des opérations longues
- Utiliser les background tasks (`ProgressManager`, coroutines) pour les scans
- Toutes les modifications de modèle projet → write action uniquement
- Les read actions peuvent se faire hors EDT mais doivent être wrappées si concurrent
