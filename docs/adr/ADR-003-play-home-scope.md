# ADR-003 — Scope des settings Play Home

**Date :** 2026-05-19  
**Statut :** accepted

---

## Contexte

Play Home est le répertoire d'installation de Play Framework 1.x (ex: `/opt/play-1.2.7/`). Il doit être configuré dans les settings du plugin.

La question est : ce setting doit-il être au niveau **machine** (application-level, partagé entre tous les projets) ou au niveau **projet** (project-level, spécifique à chaque projet ouvert) ?

---

## Décision

Play Home est configuré au niveau **application (machine)** via `@Service(Service.Level.APP)`.

Un override au niveau projet sera ajouté plus tard si le besoin se manifeste.

---

## Raisonnement

Play 1.x est un framework legacy. Dans la très grande majorité des cas, un développeur a **une seule installation de Play 1** sur sa machine, utilisée pour tous ses projets Play 1. Dupliquer le même chemin dans chaque projet serait source d'erreurs (chemin désynchronisé, oubli de configuration).

Le scope application garantit que le développeur configure Play Home une seule fois et que tous ses projets Play 1 en bénéficient immédiatement.

---

## Alternatives considérées

| Option | Raison du rejet |
|--------|----------------|
| Project-level uniquement | Oblige à reconfigurer pour chaque projet, source de friction |
| Project-level + .idea/ | Risque de commettre le chemin local dans Git (leak d'info machine) |
| Les deux simultanément | Compliqué pour le MVP, peut être ajouté plus tard si besoin |

---

## Conséquences

- `Play1Settings` est un `ApplicationService` (`@Service(Service.Level.APP)`)
- Settings accessibles via `Play1Settings.getInstance()` (pas `Play1Settings.getInstance(project)`)
- Stocké dans `~/.config/JetBrains/<IDE>/options/Play1Settings.xml`
- Ne pas committer dans `.idea/` — aucun risque de fuite dans Git
- Override projet à prévoir dans une version future si un développeur a plusieurs Play Home (rare mais possible avec différentes versions patchées)

---

## Impact Remote Development

En mode Remote Development (SSH), le Play Home configuré est celui de la machine distante (le backend). C'est le comportement attendu — le plugin s'exécute sur le backend, pas sur le poste local.

---

## Références

- [Persisting State of Components](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html)
- ADR-004 pour la discussion sur le scope des fichiers routes (project-level par nature)
