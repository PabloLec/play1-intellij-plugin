# ADR-004 — Support conf/routes via Custom FileType

**Date :** 2026-05-19  
**Statut :** accepted

---

## Contexte

Le fichier `conf/routes` de Play 1 a un format propriétaire. Pour l'améliorer dans l'éditeur (coloration, navigation, complétion), plusieurs approches sont possibles.

---

## Décision

Utiliser un **Custom FileType + Language** dédié, avec un Lexer et un ParserDefinition propres.

**ID du langage :** `PlayRoutes`  
**Détection :** par nom de fichier (`routes`), pas par extension.

---

## Alternatives considérées

| Option | Raison du rejet |
|--------|----------------|
| **Language Injection** (injecter dans un fichier .txt ou properties) | Ne permet pas de navigation PSI propre, complétion difficile, coloration limitée |
| **Annotateur seul sans langage** | Fonctionne pour la coloration basique mais impossible d'implémenter la navigation Ctrl+Click correctement |
| **Réutiliser un langage existant** (Properties, YAML) | Le format routes n'est ni Properties ni YAML — les parsers ne correspondent pas |
| **Custom FileType sans Language** | Possible pour coloration simple, mais bloque la navigation PSI et la complétion |

---

## Raisonnement

La navigation Ctrl+Click (`Controller.action` → méthode Java) et la complétion des controllers nécessitent un vrai arbre PSI. Sans PSI, les références ne peuvent pas être résolues proprement. Un Custom Language est le seul moyen de faire cela correctement avec les APIs IntelliJ.

La complexité ajoutée (Lexer + ParserDefinition) est justifiée par la valeur : navigation et complétion dans `conf/routes` est une feature MVP explicitement demandée.

---

## Format routes Play 1 supporté (MVP)

```
# Commentaire
GET     /path                   Controller.action
POST    /path                   Controller.action
*       /path/{param}           Controller.action
GET     /public/                staticDir:public
*       /module                 module:name
```

Tokens à définir :
- `HTTP_METHOD` : GET, POST, PUT, DELETE, HEAD, WS, *
- `PATH` : la partie /chemin
- `PATH_PARAM` : `{param}` ou `{<regex>param}`
- `CONTROLLER_ACTION` : `Controller.action`
- `STATIC_DIR` : `staticDir:...`
- `MODULE_ROUTE` : `module:...`
- `COMMENT` : lignes commençant par `#`
- `WHITESPACE`

---

## Conséquences

- Créer `RoutesLanguage.kt`, `RoutesFileType.kt`, `RoutesLexer.kt`, `RoutesSyntaxHighlighter.kt`, `RoutesParserDefinition.kt`
- Le Lexer peut être écrit manuellement (le format est simple) — pas besoin de JFlex dans un premier temps
- Navigation via `PsiReferenceContributor` sur l'élément `CONTROLLER_ACTION`
- Complétion via `CompletionContributor` dans la position controller/action

---

## Références

- [Custom Language Support Tutorial](https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html)
- [Simple Language Plugin Example](https://github.com/JetBrains/intellij-sdk-code-samples/tree/main/simple_language_plugin)
