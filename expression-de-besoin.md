# Spécification — Play v1 Toolkit pour IntelliJ IDEA Ultimate

## 1. Objectif général

Créer un plugin IntelliJ IDEA Ultimate, ou un ensemble d’outillages autour d’IntelliJ, permettant de travailler confortablement sur des applications legacy Java basées sur Play Framework 1.x.

L’objectif est d’obtenir une expérience développeur la plus proche possible d’une stack moderne Spring Boot + Maven dans IntelliJ IDEA Ultimate, même si Play 1 est un framework ancien, non standard Maven/Gradle dans beaucoup de projets, et moins bien supporté nativement aujourd’hui.

Le plugin doit viser une expérience simple, claire, guidée et moderne :

* le projet Play 1 est détecté automatiquement ;
* les imports Play 1 sont résolus automatiquement ;
* les sources et la documentation Play sont attachées au projet ;
* l’application peut être lancée et débuggée depuis IntelliJ ;
* les routes, controllers et views sont navigables ;
* les erreurs classiques sont signalées dans l’éditeur ;
* le développeur dispose d’une vue synthétique du projet ;
* les actions courantes Play 1 sont accessibles sans connaître les commandes historiques ;
* le plugin aide un développeur moderne à comprendre un projet legacy Play 1.

Nom du projet : **Play v1 Toolkit**.

Promesse produit :

> Modern IntelliJ support for legacy Play Framework 1 applications.

---

## 2. Contexte fonctionnel

Le besoin vient d’un contexte d’arrivée sur une application legacy Java utilisant Play Framework, probablement Play 1.x ou une version ancienne de Play.

Le développeur utilise IntelliJ IDEA Ultimate et souhaite retrouver une expérience proche de celle qu’il connaît sur des stacks modernes :

* Spring Boot ;
* Maven ou Gradle ;
* résolution automatique des dépendances ;
* navigation fluide entre code, configuration et endpoints ;
* run/debug intégré ;
* inspections dans l’éditeur ;
* autocomplétion ;
* Javadoc au survol ;
* compréhension rapide de l’architecture applicative.

Aujourd’hui, à l’ouverture d’un projet Play 1 dans IntelliJ, certains imports comme :

```java
import play.mvc.*;
```

peuvent apparaître en rouge. IntelliJ ne connaît pas les classes Play parce que les JARs du framework ne sont pas attachés au classpath du module.

Le plugin doit corriger ce problème dès le départ.

---

## 3. Principes de design UX

Le plugin doit être pensé pour la developer experience avant tout.

### 3.1. Zéro friction

À l’ouverture d’un projet, le plugin doit détecter automatiquement qu’il s’agit probablement d’un projet Play 1 et proposer une action claire :

> Play 1 project detected. Configure Play v1 Toolkit?

Le développeur ne doit pas avoir à comprendre immédiatement toute la mécanique interne du framework.

### 3.2. Réparation guidée

Le plugin doit proposer une action principale :

> Repair Play 1 Project Setup

Cette action doit faire autant de choses que possible automatiquement :

* détecter Play Home ;
* attacher les JARs Play ;
* attacher les sources Play ;
* attacher les bibliothèques du dossier `lib/` ;
* marquer les dossiers source ;
* créer une run configuration ;
* vérifier la présence de `conf/routes` et `conf/application.conf` ;
* afficher un rapport clair.

### 3.3. Expérience moderne

Le développeur doit pouvoir travailler comme sur une application moderne :

* bouton Run ;
* bouton Debug ;
* navigation depuis les endpoints ;
* inspections visibles dans l’éditeur ;
* tool window dédiée ;
* accès rapide aux routes ;
* accès rapide aux controllers ;
* génération de requêtes HTTP ;
* export d’un résumé projet.

### 3.4. Pas de magie dangereuse

Le plugin ne doit pas modifier le code applicatif sans demande explicite.

Il peut modifier la configuration IntelliJ locale :

* libraries ;
* module dependencies ;
* source roots ;
* run configurations ;
* settings du plugin.

Mais il ne doit pas modifier automatiquement :

* `conf/application.conf` ;
* `conf/routes` ;
* `conf/dependencies.yml` ;
* code Java ;
* templates ;
* scripts de build.

Les quick fixes peuvent proposer de créer une route, une méthode controller ou une vue, mais uniquement après action explicite du développeur.

---

## 4. Périmètre cible

### 4.1. Inclus

Le plugin cible Play Framework 1.x, avec les conventions classiques :

```text
app/controllers
app/models
app/views
conf/routes
conf/application.conf
conf/dependencies.yml
public
test
lib
```

Il doit particulièrement aider sur :

* classpath Play ;
* run/debug ;
* routes ;
* controllers ;
* views/templates ;
* configuration ;
* dépendances Play 1 ;
* onboarding projet.

### 5. Hors périmètre définitif

Le plugin est exclusivement destiné à améliorer l’expérience de développement sur des projets Play Framework 1.x dans IntelliJ IDEA.

Les sujets suivants sont explicitement hors périmètre et ne doivent pas être considérés comme des évolutions futures du projet :

* support de Play 2 ou Play 3 ;
* migration de Play 1 vers Spring Boot ;
* migration de Play 1 vers Maven ou Gradle ;
* remplacement du système de build Play 1 ;
* transformation automatique d’une application Play 1 legacy vers une stack moderne ;
* support exhaustif de tous les modules Play tiers ;
* création d’un framework alternatif à Play 1.

Le projet doit rester concentré sur un objectif clair : rendre un projet Play 1 existant beaucoup plus agréable, navigable, lançable et compréhensible dans IntelliJ IDEA Ultimate.

---

## 16. Guidelines d’architecture et de maintenabilité du plugin

Cette section donne des guidelines de réalisation. Elle ne doit pas être interprétée comme une arborescence obligatoire. L’agent IA chargé de réaliser le projet garde la liberté de proposer une organisation plus pertinente, tant qu’elle reste maintenable, testable et conforme aux pratiques actuelles de l’écosystème IntelliJ Platform.

### 16.1. Base projet recommandée

Le projet doit partir d’une base moderne et maintenue :

* Kotlin ;
* Gradle ;
* IntelliJ Platform Gradle Plugin 2.x ;
* IntelliJ Platform Plugin Template ou structure équivalente ;
* `plugin.xml` propre et minimal ;
* CI dès le début ;
* tâches de vérification plugin ;
* tests automatisés.

JetBrains recommande l’IntelliJ Platform Plugin Template pour accélérer la création d’un plugin Gradle, avec scaffold, CI GitHub Actions, documentation liée et organisation projet déjà préparée. Le template contient aussi des exemples de startup activity, service projet, tool window, tests fonctionnels, tests UI, run/debug configurations et workflows CI. Il faut s’en inspirer fortement, sans recopier aveuglément.

L’IntelliJ Platform Gradle Plugin 2.x est le standard actuel pour construire, tester, vérifier, configurer les environnements et publier des plugins IntelliJ. Il remplace l’ancien Gradle IntelliJ Plugin 1.x, qui n’est plus activement développé. Le plugin doit donc partir sur la génération moderne, pas sur les anciens tutoriels ou vieux exemples Gradle.

### 16.2. Organisation interne souple

Le plugin doit être découpé par responsabilités, sans imposer une arborescence figée. Les responsabilités minimales à isoler sont :

* détection projet Play 1 ;
* configuration projet et classpath ;
* settings Play Home ;
* run/debug ;
* actions utilisateur ;
* support `conf/routes` ;
* résolution routes/controllers/views ;
* tool window ;
* diagnostics ;
* tests.

L’objectif est d’éviter un plugin “gros fichier unique”. Les classes doivent être petites, nommées clairement et regroupées par intention métier.

### 16.3. S’inspirer des exemples officiels JetBrains

JetBrains maintient le dépôt `intellij-sdk-code-samples`, qui présente des exemples autonomes pour les principales APIs de plugin : actions, inspections, intentions, editor APIs, facets, framework support, tool windows, etc. Le projet doit utiliser ces samples comme source de référence pour les patterns IntelliJ Platform.

Pour chaque feature non triviale, l’agent IA doit chercher le sample JetBrains correspondant avant d’implémenter :

* Action System pour les menus `Tools > Play v1 Toolkit` ;
* Project Service pour l’état projet ;
* Tool Window pour la vue Play 1 ;
* LocalInspectionTool pour les inspections ;
* CompletionContributor pour la complétion ;
* PsiReferenceContributor pour la navigation ;
* LineMarkerProvider pour les gutter icons ;
* RunConfigurationType pour le run/debug.

### 16.4. Prendre en compte Remote Development / Split Mode

Les nouveaux plugins IntelliJ doivent être conçus en tenant compte de Remote Development / Split Mode avant de figer les frontières de modules et les choix UI. Le plugin doit donc éviter autant que possible les hypothèses trop fortes sur le filesystem local, les chemins absolus ou l’exécution locale, sauf lorsque c’est explicitement nécessaire pour Play Home.

Conséquence :

* centraliser l’accès au filesystem via les APIs IntelliJ ;
* distinguer configuration projet et configuration machine ;
* ne pas disperser les chemins Play Home dans tout le code ;
* prévoir des messages clairs si une feature nécessite un Play Home local.

### 16.5. Tests attendus

Le projet doit intégrer des tests dès le départ.

Types de tests attendus :

#### Tests unitaires purs

Pour toute logique indépendante d’IntelliJ :

* parsing simplifié de routes ;
* résolution de chemins de vues ;
* détection de structure Play 1 ;
* détection de JAR contenant `play/mvc/Controller.class` ;
* construction d’un diagnostic projet.

Ces tests doivent être rapides et ne pas démarrer d’IDE.

#### Tests IntelliJ Platform

Pour les comportements dépendant de l’IDE :

* action Repair Project Setup ;
* ajout de libraries au module ;
* configuration de source roots ;
* completion dans `conf/routes` ;
* navigation route -> controller ;
* inspection route non résolue ;
* line marker sur une méthode controller.

L’IntelliJ Platform Gradle Plugin 2.x fournit une extension `intellijPlatformTesting` permettant de déclarer des tâches dédiées pour lancer l’IDE, des tests unitaires, des tests UI ou des tests de performance, avec sandbox isolée. Le projet doit utiliser cette approche pour éviter des tests fragiles couplés à l’environnement local.

#### Tests UI, uniquement si nécessaire

Les tests UI doivent rester limités aux parcours critiques :

* ouverture d’un projet Play 1 sample ;
* notification de détection ;
* action Repair ;
* affichage du rapport ;
* création d’une run configuration.

Ne pas surinvestir les tests UI au début : ils sont plus lents et plus fragiles.

### 16.6. Projet de test Play 1 embarqué

Le repository du plugin doit contenir ou générer un mini-projet Play 1 de test, suffisamment réaliste pour valider les features.

Exemple minimal :

```text
sample-play1-app/
  app/controllers/Application.java
  app/controllers/Patients.java
  app/models/Patient.java
  app/views/Application/index.html
  app/views/Patients/show.html
  conf/application.conf
  conf/routes
  conf/dependencies.yml
  lib/
```

Ce sample doit permettre de tester :

* détection Play 1 ;
* résolution de `import play.mvc.*` via une fausse ou vraie library Play ;
* routes vers controllers ;
* `render()` vers vue implicite ;
* route cassée ;
* vue manquante ;
* dependencies.yml présent.

Pour éviter de dépendre d’un vrai Play Home dans tous les tests, le projet peut fournir un petit JAR de test contenant des classes minimales `play.mvc.Controller`, `play.Play`, etc., ou générer ce JAR pendant les tests.

### 16.7. Vérification de compatibilité

Le projet doit intégrer Plugin Verifier dans la CI. JetBrains documente `verifyPlugin` et `runPluginVerifier` pour vérifier la compatibilité binaire d’un plugin avec des builds IntelliJ ciblés. Cette vérification doit être traitée comme un quality gate, pas comme une tâche optionnelle.

Objectif : éviter qu’une API interne ou obsolète casse le plugin à la prochaine version d’IntelliJ.

### 16.8. Règles de maintenabilité

L’agent IA doit respecter ces principes :

* utiliser les APIs publiques IntelliJ autant que possible ;
* éviter les APIs annotées `@ApiStatus.Internal`, sauf justification documentée ;
* isoler les accès aux APIs IntelliJ difficiles à tester ;
* éviter les Singletons globaux non maîtrisés ;
* privilégier les Project Services pour l’état lié au projet ;
* garder les parsers simples au début ;
* ne pas bloquer l’UI thread avec des scans projet longs ;
* utiliser les mécanismes IntelliJ de background tasks quand nécessaire ;
* documenter les décisions techniques importantes dans un dossier `docs/`.

### 16.9. Documentation projet attendue

Le repository doit contenir :

* `README.md` : installation, lancement, développement, limites ;
* `docs/architecture.md` : architecture retenue, sans surdocumenter ;
* `docs/testing.md` : stratégie de test ;
* `docs/intellij-platform-notes.md` : notes sur les APIs IntelliJ utilisées ;
* `docs/play1-assumptions.md` : conventions Play 1 prises en charge ;
* `CHANGELOG.md` si publication envisagée.

Ces documents doivent rester courts, utiles et maintenus.

---

## 16. Concepts IntelliJ Platform à utiliser

Cette section décrit à quel type de customisation IntelliJ chaque besoin correspond.

### 16.1. Détection de projet

Type IntelliJ :

```text
Project Service
StartupActivity / ProjectActivity
VirtualFileManager / VFS scan
```

Rôle : détecter qu’un projet ouvert ressemble à un projet Play 1.

Critères forts :

```text
conf/application.conf
conf/routes
conf/dependencies.yml
app/controllers
app/views
```

Critères complémentaires :

```text
imports play.mvc.*
extends Controller
render()
@OnApplicationStart
play.server.Server
```

Résultat : activation du plugin pour le projet courant.

---

### 16.2. Settings du plugin

Type IntelliJ :

```text
PersistentStateComponent
Configurable
Settings page
```

Créer une page :

```text
Settings > Tools > Play v1 Toolkit
```

Champs :

```text
Play Home
Play executable
Default play id
Default HTTP port
Default debug port
Default JVM options
Auto-attach Play libraries
Auto-attach project lib folder
Auto-create run configuration
Auto-detect routes file
```

Fonctions :

* bouton “Auto-detect Play Home” ;
* bouton “Validate Play Installation” ;
* bouton “Repair Current Project” ;
* affichage de la version Play détectée si possible.

---

### 16.3. Attachement des bibliothèques Play

Type IntelliJ :

```text
Project Model API
Library API
Module dependencies
ModifiableRootModel
OrderEntry
```

Objectif : résoudre les imports comme :

```java
import play.mvc.*;
```

Le plugin doit :

1. localiser les JARs du framework Play ;
2. localiser les JARs dans `framework/lib` ;
3. localiser les JARs du projet dans `lib/` ;
4. créer une library IntelliJ “Play 1 Framework” ;
5. l’ajouter aux dependencies du module ;
6. attacher les sources Play si disponibles ;
7. éventuellement attacher la Javadoc si disponible.

Chemins typiques à inspecter :

```text
$PLAY_HOME/framework
$PLAY_HOME/framework/lib
$PLAY_HOME/framework/src
$PROJECT_DIR/lib
```

Détection du JAR principal : chercher un JAR contenant :

```text
play/mvc/Controller.class
play/Play.class
play/server/Server.class
```

Après attachement, IntelliJ doit permettre :

* résolution des imports `play.*` ;
* Ctrl+Click vers les classes Play ;
* autocomplétion des classes Play ;
* signatures visibles ;
* Javadoc / source au survol si attachées.

Feature UX :

```text
Tools > Play v1 Toolkit > Attach Play Framework Libraries
```

Feature supérieure :

```text
Tools > Play v1 Toolkit > Repair Project Setup
```

---

### 16.4. Configuration automatique des modules

Type IntelliJ :

```text
Project Model API
ModuleRootManager
ModifiableRootModel
SourceFolder
ContentEntry
```

Le plugin doit configurer correctement :

```text
app/      -> source root
conf/     -> resource root
public/   -> resource/static root si pertinent
test/     -> test source root
lib/*.jar -> module libraries
```

L’objectif est d’éviter un projet rouge et inexploitable après ouverture.

Action utilisateur :

```text
Repair Play 1 Project Setup
```

Rapport attendu :

```text
Source roots configured:
- app
- test

Resources configured:
- conf
- public

Libraries attached:
- Play 1 Framework
- Project lib/*.jar
```

---

### 16.5. Run configurations Play 1

Type IntelliJ :

```text
RunConfigurationType
ConfigurationFactory
RunProfileState
ProgramRunner
Executor
```

Créer deux types de run configurations.

#### 16.5.1. Play v1 Application

Mode intégré JVM.

Champs :

```text
Application path
Play Home
Play ID
HTTP port
JVM options
Environment variables
Working directory
Before launch action
```

Stratégie : lancer :

```text
Main class: play.server.Server
VM options:
  -Dapplication.path=<project>
  -Dplay.id=<id>
```

Avantages :

* meilleure intégration IntelliJ ;
* debug plus simple ;
* contrôle JVM.

#### 16.5.2. Play 1 Command

Mode CLI.

Commandes :

```text
play run
play test
play clean
play deps
play idealize
```

Avantages :

* plus fidèle aux usages historiques ;
* respecte les scripts Play ;
* utile en projet legacy.

---

### 16.6. Debug Play 1

Type IntelliJ :

```text
Run/debug configuration
Remote JVM debug support
ProcessHandler
ConsoleView
```

Le plugin doit permettre :

* lancer en mode debug ;
* attacher un remote debugger ;
* configurer le port debug ;
* gérer les options JVM JDWP.

Options JVM typiques :

```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

UX souhaitée :

```text
Debug Play v1 App
```

Le développeur ne doit pas écrire lui-même les options JDWP.

---

### 16.7. Actions IntelliJ

Type IntelliJ :

```text
Action System
AnAction
ActionGroup
Toolbar actions
Context menu actions
```

Créer un menu :

```text
Tools > Play v1 Toolkit
```

Actions :

```text
Repair Project Setup
Attach Play Framework Libraries
Run App
Debug App
Restart App
Stop App
Run Tests
Resolve Dependencies
Clean
Open Application in Browser
Open Routes
Open application.conf
Open dependencies.yml
Generate Project Overview
```

Actions contextuelles :

* sur `conf/routes` : run route, open controller ;
* sur controller : show mapped routes ;
* sur `render()` : open view ;
* sur template : open controller action.

---

### 16.8. Tool Window Play v1 Toolkit

Type IntelliJ :

```text
ToolWindowFactory
ToolWindow
ContentManager
Swing UI / Kotlin UI DSL
```

Créer une tool window :

```text
Play 1
```

Contenu :

```text
Project Status
  Play detected: yes/no
  Play version: detected/unknown
  Play home: configured/missing
  Classpath: OK/KO
  Run config: OK/KO

Routes
  GET /login -> Security.login
  POST /login -> Security.authenticate

Controllers
  Application
  Security
  Patients
  Invoices

Views
  Application/index.html
  Security/login.html

Models
  User
  Patient
  Invoice

Jobs
  Bootstrap
  ScheduledTask

Diagnostics
  Unresolved routes
  Missing views
  Missing Play libraries
  Missing app source root
```

Boutons :

```text
Repair
Run
Debug
Test
Open Browser
Refresh Index
Export Markdown
```

---

### 16.9. Services Tool Window, optionnel

Type IntelliJ :

```text
ServiceViewContributor
Services tool window integration
```

Option avancée : intégrer l’application Play lancée dans la fenêtre Services, comme Docker, databases ou run configurations modernes.

À faire plus tard.

---

### 16.10. Support du fichier conf/routes

Type IntelliJ :

```text
Custom Language Support
FileType
Lexer
ParserDefinition
PSI
SyntaxHighlighter
Annotator
CompletionContributor
PsiReferenceContributor
LineMarkerProvider
Find Usages
Rename support, éventuellement
```

Objectif : faire de `conf/routes` un vrai fichier intelligent.

Exemple Play 1 :

```text
GET     /login              Security.login
POST    /login              Security.authenticate
GET     /patients/{id}      Patients.show
*       /admin/{action}     Admin.{action}
```

Features :

#### Coloration syntaxique

* méthode HTTP ;
* path ;
* paramètres `{id}` ;
* controller ;
* action ;
* commentaires.

#### Navigation

Ctrl+Click sur :

```text
Patients.show
```

ouvre :

```text
app/controllers/Patients.java
method show(...)
```

#### Completion

Dans `conf/routes`, proposer :

* controllers existants ;
* méthodes publiques statiques des controllers ;
* méthodes HTTP ;
* snippets de route.

#### Validation

Inspections :

* controller inexistant ;
* méthode inexistante ;
* méthode non publique ;
* méthode non statique si convention Play 1 ;
* paramètre route absent de la signature ;
* doublon de route ;
* méthode HTTP inconnue ;
* syntaxe invalide.

#### Gutter icons

Sur chaque route :

* ouvrir dans navigateur ;
* copier curl ;
* créer requête HTTP IntelliJ ;
* aller au controller.

---

### 16.11. Support des controllers Play 1

Type IntelliJ :

```text
Java PSI integration
LineMarkerProvider
Annotator
IntentionAction
ReferencesSearch / Find Usages integration
Inlay hints, optionnel
```

Détection :

```java
public class Patients extends Controller {
    public static void show(Long id) {
        render();
    }
}
```

Features :

#### Gutter routes

À côté d’une action controller, afficher :

```text
GET /patients/{id}
```

Clic : ouvre la route.

#### Navigation vers vue implicite

Dans :

```java
render();
```

Le plugin doit résoudre :

```text
app/views/Patients/show.html
```

Ctrl+Click ou intention :

```text
Open implicit Play view
```

#### Validation render

Inspection :

```text
render() target view not found
```

Quick fix :

```text
Create view app/views/Patients/show.html
```

#### Find usages enrichi

Depuis une méthode controller, `Find usages` doit idéalement retrouver :

* routes ;
* templates qui appellent `@Patients.show()` ;
* redirect/action references si détectables.

---

### 16.12. Support des templates Play 1

Type IntelliJ :

```text
Custom language injection / Template support
FileType association
SyntaxHighlighter
PsiReferenceContributor
Annotator
CompletionContributor
```

Ne pas chercher un support exhaustif au départ.

Syntaxe cible fréquente :

```html
#{extends 'main.html' /}
#{set title:'Home' /}
#{form @Patients.save()}
    <input name="name" />
#{/form}
${user.name}
```

Features MVP :

* coloration des tags `#{...}` ;
* coloration des expressions `${...}` ;
* navigation `#{extends 'main.html' /}` vers template ;
* navigation `#{include '...' /}` vers template ;
* navigation `@Controller.action()` vers méthode Java ;
* completion des controllers/actions dans `@...` ;
* inspection template référencé manquant ;
* inspection action référencée manquante.

Features avancées :

* completion des variables passées à `render(...)` ;
* validation des tags custom ;
* navigation vers tags ;
* rename léger controller/action -> templates.

---

### 16.13. Support application.conf

Type IntelliJ :

```text
Properties language integration
CompletionContributor
Annotator
DocumentationProvider
```

Objectif : améliorer `conf/application.conf`.

Features :

* completion de clés Play connues ;
* documentation rapide des clés ;
* détection des profiles `%dev`, `%test`, `%prod` ;
* affichage de la valeur effective selon Play ID courant ;
* validation des clés critiques ;
* navigation vers fichiers référencés ;
* warning sur secret hardcodé, optionnel.

Exemples de clés :

```text
application.name
application.mode
http.port
jpda.port
db.default.url
db.default.user
db.default.password
jpa.default
hibernate.*
mail.*
```

---

### 16.14. Support dependencies.yml

Type IntelliJ :

```text
YAML support integration
Annotator
Action System
Tool Window panel
```

Features :

* action `play deps` ;
* affichage dépendances ;
* détection de JARs manquants ;
* lien vers dossier `lib/` ;
* diagnostic si `dependencies.yml` existe mais `lib/` vide ;
* warning si les dépendances ne sont pas attachées au module IntelliJ.

Exemple :

```yaml
require:
    - play
    - play -> secure
    - org.hibernate -> hibernate-core 3.6.10.Final
```

---

### 16.15. DocumentationProvider

Type IntelliJ :

```text
DocumentationProvider
```

Objectif : afficher de la documentation au survol ou via Quick Documentation.

Cas d’usage :

* dans `conf/routes`, sur une action : afficher controller, méthode, signature, vue implicite ;
* dans un template, sur `@Controller.action` : afficher route(s) associée(s) ;
* dans `application.conf`, sur une clé connue : afficher description ;
* sur un tag template Play : afficher usage rapide.

Exemple documentation route :

```text
Patients.show(Long id)
Mapped routes:
GET /patients/{id}
Implicit view:
app/views/Patients/show.html
```

---

### 16.16. CompletionContributor

Type IntelliJ :

```text
CompletionContributor
CompletionProvider
```

Completion attendue :

Dans `conf/routes` :

* HTTP methods ;
* controllers ;
* actions ;
* snippets.

Dans templates :

* controllers ;
* actions ;
* templates existants ;
* tags Play courants.

Dans `application.conf` :

* clés Play connues ;
* profiles ;
* chemins.

---

### 16.17. Annotators et inspections

Types IntelliJ :

```text
Annotator
LocalInspectionTool
IntentionAction
QuickFix
```

Inspections principales :

#### Routes

* controller introuvable ;
* action introuvable ;
* méthode non compatible ;
* paramètre absent ;
* route dupliquée ;
* syntaxe invalide.

#### Controllers

* action non routée ;
* `render()` sans vue ;
* vue référencée inexistante ;
* controller sans route ;
* méthode publique statique non utilisée, warning optionnel.

#### Templates

* template parent inexistant ;
* include inexistant ;
* action inexistante ;
* tag non fermé ;
* route/action renommée non synchronisée.

#### Project setup

* Play Home manquant ;
* JAR Play non attaché ;
* dossier `app/` non source root ;
* dossier `lib/` non attaché ;
* run configuration absente ;
* `conf/routes` absent ;
* `conf/application.conf` absent.

Quick fixes :

* attach Play libraries ;
* configure source roots ;
* create missing view ;
* create missing controller action ;
* create route for action ;
* open settings ;
* run play deps.

---

## 16. Index interne Play 1

Le plugin doit construire un modèle interne du projet.

### 16.1. Entités indexées

```text
PlayProject
PlayRoute
PlayController
PlayAction
PlayView
PlayModel
PlayJob
PlayConfigKey
PlayDependency
```

### 16.2. Données routes

Pour chaque route :

```text
HTTP method
Path pattern
Controller name
Action name
Parameters
Source file
Line number
Resolved Java method, if any
```

### 16.3. Données controllers

Pour chaque controller :

```text
Class name
Package
File path
Actions
Inherited Controller yes/no
```

Pour chaque action :

```text
Method name
Parameters
Return type
Modifiers
Mapped routes
Implicit view path
```

### 16.4. Données views

Pour chaque view :

```text
Path
Controller convention
Action convention
Extends target
Includes
Referenced actions
```

### 16.5. Implémentation progressive

MVP : scan simple via Project Service et cache mémoire.

Plus tard : utiliser les index IntelliJ plus avancés.

Le scan doit être rafraîchi :

* à l’ouverture du projet ;
* à la modification de `conf/routes` ;
* à la création/suppression de controller ;
* à la création/suppression de view ;
* via bouton Refresh Index.

---

## 16. Fonctionnalité centrale : Repair Play 1 Project Setup

Cette action est le cœur de l’expérience utilisateur.

Nom :

```text
Repair Play 1 Project Setup
```

Emplacement :

```text
Tools > Play v1 Toolkit > Repair Project Setup
Tool Window Play v1 Toolkit > Repair
Notification projet > Repair
```

### 16.1. Étapes

1. Détecter la racine Play 1.
2. Détecter ou demander Play Home.
3. Valider l’installation Play.
4. Trouver le JAR contenant `play.mvc.Controller`.
5. Créer/mettre à jour la library IntelliJ “Play 1 Framework”.
6. Ajouter les JARs Play au module.
7. Ajouter les JARs `$PROJECT_DIR/lib` au module.
8. Attacher les sources Play si trouvées.
9. Marquer `app/` comme source root.
10. Marquer `test/` comme test source root.
11. Marquer `conf/` comme resources.
12. Créer une run configuration “Play v1 App”.
13. Créer éventuellement une debug configuration.
14. Recharger l’index Play 1.
15. Afficher un rapport.

### 16.2. Rapport attendu

Exemple :

```text
Play v1 Toolkit Repair Report

Project: gmvet
Play project: detected
Play home: /opt/play-1.2.7
Play framework jar: found
play.mvc.Controller: resolved
Framework sources: attached
Project lib jars: 42 attached
Source root app/: configured
Test root test/: configured
Run configuration: created
Routes file: found
Application config: found

Status: OK
```

Si erreur :

```text
Play Home not configured.
Please select your Play 1 installation directory.
```

---

## 16. Run/debug UX cible

Le développeur doit pouvoir faire :

```text
Run > Play v1 App
Debug > Play v1 App
```

Sans connaître :

* `play.server.Server` ;
* `-Dapplication.path` ;
* options JDWP ;
* classpath Play ;
* script `play`.

### 16.1. Run configuration par défaut

Nom :

```text
Play v1 App
```

Valeurs :

```text
Application path: $PROJECT_DIR$
Play ID: dev
HTTP port: 9000
Debug port: 5005
Working directory: $PROJECT_DIR$
```

### 16.2. Open Browser

Après run, bouton :

```text
Open http://localhost:9000
```

### 16.3. Restart rapide

Action :

```text
Restart Play v1 App
```

---

## 16. Route Runner et HTTP Client

Depuis une route :

```text
GET /patients/{id} Patients.show
```

Le plugin doit proposer :

* ouvrir dans navigateur ;
* copier URL ;
* copier curl ;
* créer requête `.http` IntelliJ ;
* exécuter requête HTTP si possible.

Si la route contient des paramètres :

```text
/patients/{id}
```

Le plugin doit demander une valeur :

```text
id = 123
```

Puis générer :

```http
GET http://localhost:9000/patients/123
```

---

## 16. Project Overview

Le plugin doit générer une vue synthétique du projet.

### 16.1. Vue dans Tool Window

Afficher :

```text
Controllers: 42
Actions: 312
Routes: 286
Views: 198
Models: 75
Jobs: 8
Unresolved routes: 3
Missing views: 12
```

### 16.2. Export Markdown

Action :

```text
Export Play 1 Project Overview
```

Document généré :

```markdown
# Play 1 Project Overview

## Project Setup

## Routes

## Controllers

## Views

## Models

## Jobs

## Configuration

## Diagnostics

## Suggested Reading Path
```

Objectif : onboarding rapide d’un nouveau développeur.

---

## 16. Onboarding mode

Mode optionnel très utile.

Action :

```text
Tools > Play v1 Toolkit > Start Onboarding Tour
```

Le plugin guide le développeur :

1. ouvrir `conf/routes` ;
2. montrer les controllers principaux ;
3. montrer les views principales ;
4. montrer la config DB ;
5. montrer les routes non résolues ;
6. expliquer comment lancer l’application ;
7. expliquer comment debugger.

Cela peut être un simple wizard ou une checklist dans la tool window.

---

## 16. Roadmap recommandée

### Phase 1 — Foundation / Setup

Objectif : rendre le projet ouvrable et compilable dans IntelliJ.

Features :

* détection Play 1 ;
* settings Play Home ;
* attachement JARs Play ;
* attachement sources Play ;
* attachement `lib/*.jar` ;
* source roots ;
* action Repair Project Setup ;
* rapport diagnostic.

C’est la priorité absolue.

### Phase 2 — Run / Debug

Objectif : lancer et debugger depuis IntelliJ.

Features :

* run configuration Play v1 App ;
* run configuration Play 1 Command ;
* debug configuration ;
* actions Run / Debug / Test / Deps / Clean ;
* console Play ;
* open browser.

### Phase 3 — Routes intelligentes

Objectif : rendre `conf/routes` moderne et navigable.

Features :

* file type routes ;
* syntax highlighting ;
* parser léger ;
* navigation route -> controller ;
* completion controller/action ;
* inspections routes ;
* gutter open URL.

### Phase 4 — Controllers / Views

Objectif : relier Java et templates.

Features :

* gutter controller -> routes ;
* `render()` -> view ;
* template `@Controller.action` -> Java ;
* extends/include -> template ;
* inspections missing view ;
* quick fix create view.

### Phase 5 — Tool Window / Overview

Objectif : faciliter l’onboarding.

Features :

* tool window Play 1 ;
* status projet ;
* routes tree ;
* controllers tree ;
* diagnostics ;
* export markdown.

### Phase 6 — Inspections avancées

Objectif : qualité et productivité.

Features :

* inspections avancées ;
* quick fixes ;
* find usages enrichi ;
* rename léger ;
* documentation provider ;
* inlay hints.

---

## 16. Critères d’acceptation MVP

Le MVP est acceptable si, sur un projet Play 1 existant :

1. le plugin détecte automatiquement le projet ;
2. le plugin demande Play Home si nécessaire ;
3. l’action Repair Project Setup résout `import play.mvc.*;` ;
4. Ctrl+Click sur `Controller` ouvre une classe Play ou une source attachée ;
5. le dossier `app/` est reconnu comme source root ;
6. le dossier `test/` est reconnu comme test root ;
7. les JARs du dossier `lib/` sont attachés ;
8. une run configuration Play 1 est créée ;
9. l’application peut être lancée depuis IntelliJ ;
10. le plugin affiche un rapport clair dans une tool window ou notification.

MVP+ :

11. `conf/routes` a une coloration basique ;
12. Ctrl+Click sur `Controller.action` dans `conf/routes` ouvre la méthode Java ;
13. `render()` permet d’ouvrir la vue implicite ;
14. une route peut être ouverte dans le navigateur.

---

## 16. Critères de qualité

Le plugin doit :

* être écrit en Kotlin ;
* utiliser Gradle ;
* s’appuyer sur l’IntelliJ Platform Gradle Plugin ;
* être compatible avec IntelliJ IDEA Ultimate récent ;
* ne pas dépendre d’un Play installé globalement si Play Home est configuré manuellement ;
* ne pas casser les projets non Play ;
* ne pas modifier le code sans confirmation ;
* fournir des messages d’erreur compréhensibles ;
* logguer les diagnostics techniques ;
* rester robuste sur des projets legacy imparfaits.

---

## 20. Guidelines de réalisation du repository

Le projet ne doit pas figer trop tôt une arborescence complexe. L’agent IA doit proposer une organisation simple, lisible et évolutive, inspirée des templates JetBrains actuels.

Exigences :

* partir d’un projet Gradle Kotlin moderne ;
* utiliser l’IntelliJ Platform Gradle Plugin 2.x ;
* déclarer proprement les extensions dans `plugin.xml` ;
* garder une séparation claire entre logique métier Play 1 et APIs IntelliJ ;
* créer un mini-projet Play 1 de test ;
* intégrer des tests unitaires et des tests IntelliJ Platform ;
* intégrer Plugin Verifier dans la CI ;
* documenter les choix techniques dans `docs/`.

L’agent IA peut organiser les packages comme il le souhaite, mais il doit justifier les frontières retenues. Il doit privilégier une architecture sobre, progressive, et facile à modifier.

---

## 20. Risques techniques

### 20.1. Variabilité des projets Play 1

Tous les projets Play 1 ne respectent pas parfaitement les conventions.

Réponse : rendre les chemins configurables.

### 20.2. Templates difficiles à parser

Les templates Play 1 peuvent contenir beaucoup de logique dynamique.

Réponse : commencer par navigation et inspections simples, ne pas chercher un support exhaustif.

### 20.3. Version Play inconnue

Le projet peut utiliser une version patchée ou custom.

Réponse : détecter par classes présentes et chemins plutôt que par version stricte.

### 20.4. Conflits avec modules IntelliJ existants

Le plugin peut modifier le modèle projet.

Réponse : afficher un rapport, éviter les modifications destructrices, ne pas supprimer les dépendances existantes.

### 20.5. Debug Play 1 parfois instable

Le reload Play 1 peut désynchroniser le debugger.

Réponse : proposer un debug simple, documenter les limites, fournir restart rapide.

---

## 20. Résultat attendu final

À terme, un développeur doit pouvoir ouvrir un vieux projet Play 1 dans IntelliJ IDEA Ultimate et obtenir une expérience proche de :

```text
Open project
→ Play 1 detected
→ Repair project setup
→ imports resolved
→ Run app
→ Debug app
→ navigate routes/controllers/views
→ inspect missing links
→ understand project structure
```

L’expérience ne sera pas identique à Spring Boot + Maven, mais elle doit s’en rapprocher sur les points essentiels :

* classpath correct ;
* run/debug intégré ;
* navigation framework ;
* validation ;
* onboarding ;
* feedback clair.

Le premier objectif n’est pas de tout supporter. Le premier objectif est de supprimer la friction majeure : un vieux projet rouge, difficile à lancer, sans navigation entre routes, controllers et vues.

---

## 20. Prompt de mission pour un agent IA

Tu vas développer un plugin IntelliJ IDEA Ultimate nommé “Play v1 Toolkit”.

Objectif : améliorer fortement la developer experience sur des projets legacy Java basés sur Play Framework 1.x, afin de se rapprocher du confort d’une stack moderne Spring Boot + Maven.

Commence par un MVP robuste, simple et utile.

Priorité absolue :

1. détecter automatiquement un projet Play 1 ;
2. configurer Play Home ;
3. attacher automatiquement les JARs Play au module IntelliJ ;
4. attacher les sources Play si disponibles ;
5. attacher les JARs du dossier `lib/` du projet ;
6. marquer `app/` comme source root et `test/` comme test root ;
7. créer une action “Repair Play 1 Project Setup” ;
8. créer une run configuration Play 1 ;
9. permettre de lancer et debugger l’application ;
10. afficher un rapport clair de diagnostic.

Ensuite, ajoute progressivement :

* support intelligent de `conf/routes` ;
* navigation route -> controller ;
* navigation controller/render -> view ;
* support simple des templates Play 1 ;
* tool window Play 1 ;
* inspections et quick fixes.

Contraintes :

* utiliser Kotlin ;
* utiliser Gradle ;
* utiliser l’IntelliJ Platform Gradle Plugin 2.x ;
* utiliser l’IntelliJ Platform SDK ;
* s’inspirer de l’IntelliJ Platform Plugin Template ;
* intégrer Plugin Verifier dans la CI ;
* prévoir des tests unitaires, tests IntelliJ Platform et éventuellement tests UI ciblés ;
* ne pas modifier le code applicatif sans confirmation ;
* privilégier une UX simple et moderne ;
* documenter chaque décision technique ;
* maintenir une roadmap claire ;
* fournir des tests quand c’est pertinent ;
* produire un README expliquant comment développer, lancer et tester le plugin.

Le plugin doit être pensé pour de vrais projets legacy, potentiellement imparfaits, avec une priorité forte sur la robustesse, le diagnostic et la clarté pour le développeur.
