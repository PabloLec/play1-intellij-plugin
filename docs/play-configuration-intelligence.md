# Play Configuration Intelligence

## Objectif

Fournir dans IntelliJ IDEA une expérience complète de navigation, compréhension et validation de la configuration Play 1, proche de ce qu'IntelliJ IDEA Ultimate propose sur les propriétés Spring Boot, mais adaptée à Play Framework 1 et au fichier `conf/application.conf`.

## Comparaison avec Spring Boot Properties

| Fonctionnalité | Spring Boot (IU) | Play Config Intelligence |
|---|---|---|
| Ctrl+Click Java → conf | ✅ | ✅ |
| Find Usages conf → Java | ✅ | ✅ |
| Rename propagé | ✅ | ✅ |
| Complétion Java | ✅ | ✅ |
| Inlay hints valeur | ✅ | ✅ |
| Quick documentation | ✅ | ✅ |
| Profils / environnements | Profiles Spring | Play IDs (`%docker.key`) |

| Unused warning | ✅ | ✅ (conservatif) |
| Format fichier | YAML/Properties | Play 1 custom syntax |

## Spécificités Play 1

- Le fichier de configuration est **toujours** `conf/application.conf`
- Syntaxe proche des `.properties` Java mais **pas HOCON** (pas de Play 2)
- Profils activés via `%profile.key=value`, pas via Spring Profiles
- Le profil actif est transmis via `-Dplay.id=docker` au démarrage du serveur
- Les commentaires supportés sont `#` et `;`

## Syntaxe supportée

```
# Commentaire hash
; Commentaire semicolon

# Propriété simple
application.mode=dev

# Avec espaces autour de =
hibernate.show_sql = false

# Propriété profilée
%docker.db.url=${DATABASE_URL}
%linux.db.url=jdbc:mysql://localhost:3306/gmvet
%dev.application.mode=dev
%prod.application.mode=prod

# Valeur vide
job.force.debug=

# Substitution d'environnement
db.pass=${DATABASE_PASSWORD}
db.url=jdbc:${DB_TYPE}://${DB_HOST}:3306/${DB_NAME}

# Valeurs complexes (URLs JDBC, cron, JVM options)
db.url=jdbc:mysql://host:3306/db?useSSL=false&allowPublicKeyRetrieval=true
cron.export.tarifs=0 0 21 ? * 7
jvm.memory=-Xmx512m -Xms256m
```

## Règles de résolution profilée

Pour une clé `foo.bar` avec profil actif `docker` :

1. Si `%docker.foo.bar=value` existe → valeur effective = valeur profilée
2. Sinon si `foo.bar=value` existe → valeur effective = valeur par défaut
3. Si aucune n'existe → clé non résolue

La résolution du profil actif suit l'ordre de priorité :
1. La Run Configuration Play sélectionnée dans RunManager (champ `Play ID`)
2. Le setting projet "Active framework profile" dans Configuration Intelligence
3. Aucun profil (affichage de la valeur par défaut + indication des overrides)

## APIs IntelliJ utilisées

| API | Usage |
|---|---|
| `Language` / `LanguageFileType` | Langage custom PlayConfig pour `application.conf` |
| `LexerBase` | Tokenisation Play 1 (KEY, VALUE, ENV_PLACEHOLDER, COMMENT...) |
| `ParserDefinition` / `PsiParser` | AST → PSI (nœuds PROPERTY) |
| `PsiNameIdentifierOwner` | `PlayConfigProperty.getName()` = logicalKey |
| `PsiReferenceContributor` | Références Java → conf |
| `PsiPolyVariantReference` | Navigation multi-cibles (profil actif vs overrides) |
| `ReferencesSearch` | Find Usages conf → Java |
| `RenamePsiElementProcessor` | Rename logique avec choix profil/tout |
| `FactoryInlayHintsCollector` | Hints Java (valeurs effectives) + hints conf (markers) |
| `DocumentationTargetProvider` | Quick doc sur conf et Java |
| `CompletionContributor` | Complétion Java et conf |
| `LocalInspectionTool` | 7 inspections (unused, duplicate, unresolved env var, etc.) |
| `RelatedItemLineMarkerProvider` | Gutter icons dans conf |
| `CachedValuesManager` | Cache invalidé sur modification PSI |
| `DumbService.isDumb()` | Protection mode dumb (index non prêts) |
| `SmartPointerManager` | Pointers PSI pour documentation target |

## Structure technique

```
playconfig/
├── lang/           Langage PSI (Language, FileType, Lexer, Parser, TokenTypes, SyntaxHighlighter)
├── psi/            Éléments PSI (PlayConfigFile, PlayConfigProperty)
├── model/          Modèle métier (PlayConfigKey, PlayConfigResolution, PlayConfigWrapperMethod)
├── service/        Service central (PlayConfigService, PlayConfigKnownKeys)
├── settings/       Settings projet (PlayConfigProjectSettings, Configurable, Panel)
├── references/     Références (Contributor, Reference, FindUsages, RenameProcessor, UsageSearcher)
├── completion/     Complétion (Java, PlayConfig)
├── hints/          Inlay hints (Java valeurs, PlayConfig markers)
├── documentation/  Quick doc (DocumentationTargetProvider)
├── inspections/    8 inspections + QuickFix
└── lineMarker/     Gutter icons (PlayConfigLineMarkerProvider)
```

## Tests couverts

| Test | Fichier |
|---|---|
| Lexer : simple, espaces, `#`, `;`, profilée, `${ENV}`, invalide | `PlayConfigLexerTest` |
| Parsing rawKey → profile + logicalKey | `PlayConfigPropertyTest` |
| PlayConfigKnownKeys (known keys + prefixes) | `PlayConfigServiceTest` |
| extractEnvVarNames placeholders | `PlayConfigServiceTest` |

## Limites connues de l'analyse statique

1. **Clés dynamiques** : `Play.configuration.getProperty("prefix." + suffix)` ne peut pas être résolu statiquement. L'usage est ignoré (pas de faux warning côté conf).

2. **Unused conservatif** : les clés consommées dynamiquement ou via des librairies non reconnues ne sont pas détectées comme "used". L'inspection unused est `WEAK_WARNING` et respecte la liste des clés framework connues.

3. **Résolution des env vars** : limitée aux variables disponibles dans la Run Configuration Play sélectionnée ou dans l'environnement du process IDE. Les variables disponibles seulement en CI/CD sont marquées "unresolved".

4. **Valeurs multilignes** : Play 1 ne supporte pas les valeurs multilignes. Le parser ne les supporte pas non plus.

5. **Includes/imports** : Play 1 ne supporte pas `@include`. Un seul fichier `conf/application.conf` est analysé.

6. **Rename avec réflexion** : les usages dynamiques via `Class.forName` ou Spring-like DI ne sont pas trouvés par Find Usages.

## Exemples d'usage

### Navigation Java → conf

```java
// Ctrl+Click sur "db.url" navigue vers application.conf:db.url
Play.configuration.getProperty("db.url")

// Si profil actif = docker, navigue vers %docker.db.url en priorité
Play.configuration.getProperty("db.url")
```

### Inlay hints

```java
Play.configuration.getProperty("application.mode")       // = dev
Play.configuration.getProperty("db.url")                 // = jdbc:mysql://localhost/db
Play.configuration.getProperty("application.secret")     // = s1kwayg...
Play.configuration.getProperty("db.pass")               // = ${DATABASE_PASSWORD}
Play.configuration.getProperty("unknown.key")            // /* unresolved config key */
Play.configuration.getProperty("http.port")              // /* default: 9000 · overrides: docker, prod */
```

### Wrapper custom

```
Settings > Play v1 Toolkit > Configuration Intelligence > Custom Config Wrappers
→ Class: com.myapp.ConfigUtils   Method: getString   Key Arg Index: 0
```

Résultat : `ConfigUtils.getString("db.url")` est reconnu comme usage de la clé `db.url`.
