# Play Messages Support

## Objectif

Fournir dans IntelliJ IDEA une expérience complète de navigation, compréhension et validation des fichiers d'internationalisation Play 1.

## Fichiers supportés

| Fichier | Locale |
|---|---|
| `conf/messages` | Défaut (fallback) |
| `conf/messages.fr` | Français |
| `conf/messages.en-US` | Anglais américain |
| `conf/messages.<locale>` | Toute locale |

Seuls les fichiers sous `conf/` sont reconnus comme fichiers de messages Play 1.

## Syntaxe supportée

```properties
# Commentaire hash
; Commentaire semicolon

# Propriété simple
hello=Hello World

# Avec espaces autour de =
greeting = Bonjour le monde

# Avec placeholders Java (%s, %d, %f, etc.)
user.greeting=Hello %s!
item.count=You have %d item(s) in cart

# %% est un signe % littéral (pas un placeholder)
progress=Progress: 100%%

# Valeur vide
key.empty=
```

## Usages supportés

### Java

```java
// import play.i18n.Messages;
Messages.get("hello")
Messages.get("user.greeting", user.name)
Messages.get("item.count", cart.size())
play.i18n.Messages.get("hello")
```

### Templates Play 1 (app/views/**/*.html)

```html
<h1>&{'hello'}</h1>
<p>&{'user.greeting', user.name}</p>
<span>&{"hello"}</span>
```

### JavaScript (non supporté)

`i18n('key')` dans les fichiers `.js` n'est pas encore supporté. Utiliser Find Usages depuis Java/templates.

## Fonctionnalités IDE

| Fonctionnalité | Java | Templates HTML |
|---|---|---|
| Ctrl+Click → définition | ✅ | ✅ |
| Find Usages → depuis conf | ✅ | ✅ |
| Rename propagé | ✅ | ✅ |
| Complétion | ✅ | ✅ |
| Inlay hint valeur | ✅ | — |
| Quick documentation | ✅ | — |

## Stratégie de résolution

Pour une clé `hello` :
1. `conf/messages` = valeur par défaut (locale `null`)
2. `conf/messages.fr` = variante française (locale `fr`)
3. etc.

Ctrl+Click sur `"hello"` propose toutes les définitions (défaut en premier), ce qui permet de naviguer vers n'importe quelle locale.

Rename depuis une définition renomme **toutes les locales** automatiquement (sans popup de choix).

## Inspections disponibles

| Inspection | Langage | Niveau | Description |
|---|---|---|---|
| Unknown Play message key | Java | WEAK_WARNING | `Messages.get("missing")` — clé absente de conf/messages |
| Unknown Play message key (template) | HTML | WEAK_WARNING | `&{'missing'}` — clé absente |
| Duplicate Play message key | PlayMessages | WARNING | Même clé définie 2× dans le même fichier |
| Missing locale translation | PlayMessages | INFORMATION (désactivée) | Clé présente dans messages mais absente dans messages.fr |
| Placeholder count mismatch | Java | WEAK_WARNING | `Messages.get("fmt")` mais `fmt=Hello %s` attend 1 arg |

## Gutter icons

- **Globe** (icône monde) sur les clés de `conf/messages` ayant des traductions dans d'autres locales → navigue vers toutes les traductions
- **Related** (icône nœud lié) sur toute clé ayant des usages Java ou HTML → navigue vers ces usages

## Limites connues

1. **JavaScript** : `i18n('key')` non supporté dans les fichiers `.js`.
2. **Includes** : Play 1 ne supporte pas les includes de fichiers messages. Un seul `conf/messages` et ses variantes sont analysés.
3. **Clés dynamiques** : `Messages.get(keyVariable)` ne peut pas être résolu statiquement — ignoré.
4. **Multilignes** : les valeurs multilignes Java (avec `\` en fin de ligne) ne sont pas supportées.
5. **Rename HTML** : le rename dans les templates HTML fonctionne via remplacement de texte brut — fonctionne mais ne préserve pas le style de guillemets d'origine si la valeur contient des caractères spéciaux.

## Exemples de quick doc (Ctrl+Q)

Sur `Messages.get("hello")` ou sur `hello=...` dans conf/messages :

```
hello — Play 1 message key

default:    Hello World
fr:         Bonjour le monde
en-US:      Hello World

Status:     used (3 refs)
```
