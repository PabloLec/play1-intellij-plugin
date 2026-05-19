# ADR-002 — Kotlin comme langage unique

**Date :** 2026-05-19  
**Statut :** accepted

---

## Contexte

Les plugins IntelliJ peuvent être écrits en Java ou en Kotlin. Le choix du langage impacte la lisibilité, l'expressivité, et la compatibilité avec les APIs modernes IntelliJ (notamment les coroutines utilisées dans les nouvelles APIs `ProjectActivity`, `ReadAction`, etc.).

---

## Décision

Utiliser **Kotlin uniquement**. Aucun fichier Java dans les sources du plugin.

---

## Alternatives considérées

| Option | Raison du rejet |
|--------|----------------|
| Java uniquement | Verbeux, pas de coroutines natives, moins bien adapté aux nouvelles APIs IntelliJ |
| Mix Java/Kotlin | Complique le build, génère de la confusion, pas de bénéfice par rapport à Kotlin pur |

---

## Conséquences

- Le code source du plugin (pas les fixtures de test Play 1) est entièrement en Kotlin.
- Les APIs IntelliJ basées sur les coroutines (`ProjectActivity`, `ReadAction`, etc.) sont utilisées nativement.
- Le DSL Kotlin UI (`com.intellij.ui.dsl.builder`) est utilisé pour les panneaux de settings et la Tool Window.
- Les classes Java du sample Play 1 (`sample-play1-app/app/controllers/`) restent en Java — c'est du code applicatif Play, pas du code plugin.

---

## Références

- [Kotlin in IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/kotlin.html)
