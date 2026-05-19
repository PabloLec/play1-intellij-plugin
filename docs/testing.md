# Testing — Play 1 Toolkit

> Ce document décrit la stratégie de test du plugin, les outils utilisés,
> les fixtures disponibles et les commandes pour lancer les tests.

---

## Stratégie générale

Le plugin utilise deux niveaux de test :

1. **Tests unitaires purs** : logique indépendante d'IntelliJ, rapides, sans sandbox IDE
2. **Tests IntelliJ Platform** : comportements qui dépendent de l'IDE, avec sandbox légère

Les tests UI (Remote UI tests) sont réservés aux parcours critiques et ne sont pas prioritaires au début.

---

## Tests unitaires purs

### Outil
JUnit 5 (bundlé dans IntelliJ Platform Gradle Plugin 2.x).

### Principe
Les classes testées ici **n'importent pas d'API IntelliJ**. Elles travaillent sur des `Path`, `File`, `String`. Elles sont dans le package `detection/` et `model/`.

### Classes testées

| Classe de test | Classe testée | Ce qui est testé |
|----------------|---------------|-----------------|
| `Play1ProjectDetectorTest` | `Play1ProjectDetector` | détection layouts Play 1 variés |
| `RoutesParserTest` | `RoutesParser` (futur) | parsing lignes routes valides/invalides |
| `Play1HomeValidatorTest` | `Play1HomeValidator` | validation répertoire Play Home |
| `Play1LibraryManagerTest` | `Play1LibraryManager` | scan JARs, détection play.jar |
| `RepairReportTest` | `RepairReport` | construction et sérialisation rapport |

### Fixtures

```
src/test/resources/
├── fixtures/
│   ├── play1-standard/        projet Play 1 complet (conf/, app/, test/, lib/)
│   ├── play1-minimal/         seulement conf/application.conf + conf/routes + app/controllers/
│   ├── play1-no-lib/          sans dossier lib/
│   ├── play1-partial/         présence partielle (1 critère sur 3)
│   └── not-play1/             projet Spring Boot ou Maven ordinaire
└── stubs/
    └── play-stub.jar          JAR minimal avec play.Play, play.mvc.Controller, play.server.Server
```

### Création du play-stub.jar

Le stub JAR est créé lors de la phase Lot 6. Il contient des classes Java minimales (corps vides) pour permettre aux tests de valider la détection du JAR Play sans installation réelle.

Généré via une tâche Gradle `generatePlayStubJar` dans `build.gradle.kts`.

Classes incluses :
```
play/Play.class
play/mvc/Controller.class
play/server/Server.class
```

---

## Tests IntelliJ Platform

### Outil
`BasePlatformTestCase` (IntelliJ Platform SDK) ou `LightPlatformTestCase`.

Le plugin utilise `intellijPlatformTesting` (fourni par IntelliJ Platform Gradle Plugin 2.x) pour définir des tâches de test isolées avec sandbox.

### Classes testées

| Classe de test | Ce qui est testé |
|----------------|-----------------|
| `RepairProjectSetupActionTest` | library attachée, source roots configurés, run config créée |
| `RoutesNavigationTest` | Ctrl+Click route → méthode Java |
| `Play1SettingsTest` | persistance des settings entre sessions |
| `Play1StartupActivityTest` | notification affichée à l'ouverture d'un projet Play 1 |

### Sample project Play 1

Les tests plateforme utilisent le contenu de `sample-play1-app/` comme projet de test.

Le répertoire est copié dans la sandbox IDE pour chaque test.

---

## Sample Play 1 application

Situé dans `sample-play1-app/` à la racine du repository.

**Origine :** adapté du projet `yabe` disponible dans `/tmp/play1-master/samples-and-tests/yabe/`.
Yabe est un blog complet avec controllers, models, vues, routes et conf.

**Contenu du sample :**
```
sample-play1-app/
├── app/
│   ├── controllers/
│   │   ├── Application.java
│   │   └── Posts.java
│   ├── models/
│   │   └── Post.java
│   └── views/
│       ├── Application/index.html
│       └── Posts/show.html
├── conf/
│   ├── application.conf
│   ├── routes
│   └── dependencies.yml
├── test/
│   └── ApplicationTest.java
└── lib/
    └── (vide ou play-stub.jar pour tests locaux)
```

**Ce qu'il permet de tester :**
- Détection Play 1 (conf/ + app/controllers/)
- Résolution de `import play.mvc.*`
- Routes vers controllers (Application, Posts)
- `render()` vers vue implicite
- Route avec paramètre (`/posts/{id}`)
- Vue manquante (intentionnellement)

---

## Tests UI (optionnel, non prioritaire)

Parcours critiques pouvant faire l'objet de tests UI :
- Ouverture `sample-play1-app/` → notification détection
- Clic "Repair" → rapport affiché
- Création run configuration

Ne pas investir dans les tests UI avant que le MVP soit stable.

---

## Commandes

```bash
# Tests unitaires (rapides, sans IDE)
./gradlew test

# Tests IntelliJ Platform (avec sandbox, plus lents)
./gradlew runIdeForUiTests

# Vérification compatibilité plugin
./gradlew verifyPlugin

# Build complet
./gradlew buildPlugin

# Lancer l'IDE de développement (sandbox)
./gradlew runIde
```

---

## Limites connues

- Les tests plateforme nécessitent le téléchargement d'une distribution IntelliJ lors du premier run (géré automatiquement par Gradle)
- Les tests dépendant d'un vrai Play Home ne peuvent pas être automatisés en CI sans installation préalable → utiliser le stub JAR
- Les templates Play (`.html`) sont difficiles à tester de manière exhaustive avec PSI → tester manuellement dans un premier temps
