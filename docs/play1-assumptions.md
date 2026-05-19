# Play 1 Assumptions — Play v1 Toolkit

> Ce document liste les hypothèses prises sur les projets Play Framework 1.x
> que le plugin prend en charge. Ces hypothèses informent les heuristiques
> de détection, les chemins attendus, et les limites du support initial.

---

## Structure d'un projet Play 1 standard

```
<project-root>/
├── app/
│   ├── controllers/        classes Java héritant de play.mvc.Controller
│   ├── models/             entités JPA/Hibernate
│   ├── views/              templates Groovy/HTML (.html)
│   └── jobs/               background jobs (@OnApplicationStart, etc.)
├── conf/
│   ├── application.conf    configuration principale (clé=valeur, profiles %dev, %prod)
│   ├── routes              fichier de routage HTTP (format spécifique Play 1)
│   └── dependencies.yml    dépendances gérées par Ivy via Play
├── lib/
│   └── *.jar               JARs supplémentaires non gérés par dependencies.yml
├── public/
│   ├── images/
│   ├── javascripts/
│   └── stylesheets/
├── test/
│   └── *.java              tests Play 1 (héritent de UnitTest, FunctionalTest, etc.)
└── modules/                modules Play 1 locaux (rare)
```

---

## Critères de détection d'un projet Play 1

Le plugin considère un projet comme Play 1 si **au moins 2** des 3 critères forts suivants sont présents :

| Critère | Chemin | Poids |
|---------|--------|-------|
| Fichier de config | `conf/application.conf` | Fort |
| Fichier de routes | `conf/routes` | Fort |
| Dossier controllers | `app/controllers/` | Fort |

Critères additionnels (confirmatoires, non obligatoires) :
- `conf/dependencies.yml`
- `app/views/`
- `app/models/`

---

## Détection Play Home

Un répertoire Play Home valide contient :

```
$PLAY_HOME/
├── framework/
│   ├── play-<version>.jar      JAR principal (ex: play-1.2.7.jar)
│   ├── lib/                    dépendances (86 JARs dans la distribution standard)
│   └── src/                    sources Play (optionnel, présent dans certaines distrib)
├── modules/                    modules officiels (crud, secure, docviewer, etc.)
└── play                        script Python de lancement
```

**Détection du JAR principal :**
- Chercher `framework/play-*.jar` dans le Play Home
- Vérifier que ce JAR contient `play/mvc/Controller.class`

**Auto-détection du Play Home (ordre de priorité) :**
1. Variable d'environnement `$PLAY_HOME`
2. Chemins courants Linux/Mac : `/opt/play-1.*`, `/usr/local/play-1.*`, `~/play-1.*`, `~/.play`
3. Windows : `C:\play-1.*`
4. Résolution via `which play` (symlink → répertoire parent)

---

## Format du fichier conf/routes

Chaque ligne valide a la structure :

```
METHOD  path  ControllerClass.actionMethod
```

Exemples :
```
GET     /                           Application.index
POST    /login                      Security.authenticate
GET     /patients/{id}              Patients.show
GET     /patients/{<[0-9]+>id}      Patients.show
*       /{controller}/{action}      {controller}.{action}
GET     /public/                    staticDir:public
*       /admin                      module:crud
*       /                           module:secure
```

**Spécificités :**
- Les commentaires commencent par `#`
- Les chemins peuvent contenir des paramètres `{param}` ou des regex `{<regex>param}`
- `staticDir:` indique un répertoire de fichiers statiques
- `module:` indique un module Play à monter à ce préfixe
- `*` comme méthode = toutes les méthodes HTTP

---

## Conventions controllers

```java
public class Patients extends Controller {
    public static void show(Long id) {
        Patient patient = Patient.findById(id);
        render(patient);  // → app/views/Patients/show.html (convention)
    }
    
    public static void list() {
        renderTemplate("Patients/list.html");  // explicite
    }
}
```

**Hypothèses :**
- Les controllers héritent de `play.mvc.Controller`
- Les actions sont des méthodes `public static void`
- `render()` sans argument → vue implicite `app/views/{ControllerName}/{actionName}.html`
- Les controllers sont dans le package `controllers` (direct ou sous-packages)
- Les paramètres de la méthode correspondent aux paramètres de route/query string

---

## Conventions views/templates

- Format : `.html` avec tags Groovy/Play (`#{tag /}`, `${expr}`, `%{code}%`)
- Emplacement : `app/views/{ControllerName}/{actionName}.html`
- Template parent : `#{extends 'main.html' /}` ou `#{extends 'Application/layout.html' /}`
- Appel controller depuis template : `@Patients.show(patient.id)`

---

## Limitations connues du support initial

1. **Modules Play** — Les modules (crud, secure, etc.) ajoutent leurs propres routes via `module:xxx`. Le parsing de ces routes n'est pas supporté dans le MVP.

2. **Controllers dynamiques** — La wildcard `{controller}.{action}` dans les routes est difficile à résoudre statiquement. Supportée partiellement.

3. **Play Home manquant en CI** — Le plugin ne peut pas valider le classpath sans Play Home. Les tests en CI utilisent un stub JAR.

4. **Versions Play 1 patchées** — Certains projets utilisent des versions patchées du framework. Le plugin détecte par présence de classes, pas par numéro de version exact.

5. **Projets multi-modules** — Les projets avec plusieurs modules Play dans un seul repo ne sont pas dans le périmètre MVP.

6. **Templates Groovy** — La logique des templates Play 1 (tags custom, expressions Groovy) est complexe. Le support initial est limité à la navigation et la coloration basique.

7. **JPA/Hibernate** — Aucun support spécifique des entités JPA dans le MVP. Les imports JPA sont résolus si les JARs correspondants sont dans `lib/`.

---

## Risques et mitigations

| Risque | Mitigation |
|--------|-----------|
| Projet Play 1 non standard (dossiers renommés) | Chemins configurables dans les settings |
| Version Play inconnue ou patchée | Détection par classes présentes, pas par version |
| Conflits avec libraries existantes | Ne pas supprimer les dépendances existantes, juste ajouter |
| Modification accidentelle du projet | Toujours afficher un rapport avant/après, ne modifier que la config IntelliJ |
| Play Home non disponible en CI | Tests unitaires avec stub JAR, skip des tests intégration si pas de Play Home |
