# ADR-001 — IntelliJ Platform Gradle Plugin 2.x

**Date :** 2026-05-19  
**Statut :** accepted

---

## Contexte

Le plugin doit être buildé avec Gradle. JetBrains fournit deux versions du plugin Gradle pour les plugins IntelliJ :

- **Gradle IntelliJ Plugin 1.x** (`org.jetbrains.intellij`) — ancien, encore largement documenté dans les tutoriels, mais plus activement développé sur le fond.
- **IntelliJ Platform Gradle Plugin 2.x** (`org.jetbrains.intellij.platform`) — version moderne, recommandée par JetBrains depuis 2024, avec une API plus propre, un meilleur support de `intellijPlatformTesting`, et une intégration CI améliorée.

---

## Décision

Utiliser **IntelliJ Platform Gradle Plugin 2.x** (`org.jetbrains.intellij.platform`).

---

## Alternatives considérées

| Option | Raison du rejet |
|--------|----------------|
| Gradle IntelliJ Plugin 1.x | Déprécié sur le fond, moins bien supporté pour les nouvelles APIs (testing, coroutines). Les tutoriels existants qui l'utilisent sont souvent obsolètes. |
| Maven | Non supporté officiellement par JetBrains pour les plugins IntelliJ. |

---

## Conséquences

- La configuration `build.gradle.kts` utilise le DSL `intellijPlatform { }` (nouveau) au lieu de `intellij { }` (ancien).
- Les tâches `verifyPlugin`, `runIde`, `buildPlugin` fonctionnent différemment — se référer à la documentation officielle v2.x.
- `intellijPlatformTesting` est disponible pour déclarer des sandbox de test isolées.
- Les dépendances IntelliJ sont déclarées via `intellijPlatform { }` dans `dependencies { }`, pas via `intellij { version = "..." }`.

**Exemple `build.gradle.kts` :**
```kotlin
plugins {
    id("org.jetbrains.intellij.platform") version "2.x.x"
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2024.1")
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        zipSigner()
    }
}
```

---

## Références

- [IntelliJ Platform Gradle Plugin 2.x documentation](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Migration guide 1.x → 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html)
