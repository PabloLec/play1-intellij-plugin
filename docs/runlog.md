# Runlog — Play 1 Toolkit

> Journal chronologique d'exécution. Toute personne (développeur ou agent IA) reprenant
> ce projet doit pouvoir lire ce fichier et comprendre ce qui a été fait, pourquoi,
> et ce qui reste à faire sans relire tout l'historique Git.

---

## 2026-05-19 — Lot 0 : Analyse initiale et documentation

### Contexte
Premier démarrage du projet. Le repository ne contenait que `expression-de-besoin.md`.

### Actions réalisées

**Lecture du besoin :**
- Lu intégralement `expression-de-besoin.md` (1760 lignes)
- Le document décrit un plugin IntelliJ IDEA Ultimate pour Play Framework 1.x
- Objectif principal : supprimer la friction à l'ouverture d'un vieux projet Play 1 (imports rouges, pas de run config, pas de source roots)
- Action centrale identifiée : **Repair Play 1 Project Setup**
- Roadmap officielle en 6 phases (Foundation → Run/Debug → Routes → Controllers/Views → Tool Window → Inspections avancées)

**Exploration de l'environnement :**
- Samples Play 1 disponibles localement dans `/tmp/play1-master/samples-and-tests/` (17 projets, dont `yabe`)
- Framework Play 1 en source non compilée dans `/tmp/play1-master/framework/src/`
- Constat important : `/tmp/play1-master` contient des **sources non compilées**. Un vrai Play Home installé (ex: `/opt/play-1.2.7/`) contient `framework/play-1.2.7.jar` compilé. Le plugin doit cibler ce JAR.
- 86 JARs de dépendances dans `framework/lib/`
- Sample `yabe` : blog complet avec 7 controllers, 4 models, vues, routes, conf/

**Décisions prises :**
- ADR-001 : IntelliJ Platform Gradle Plugin 2.x (org.jetbrains.intellij.platform)
- ADR-002 : Kotlin uniquement
- ADR-003 : Settings Play Home au niveau application (machine), override projet possible
- ADR-004 : Custom FileType pour `conf/routes` (pas Language Injection)
- ADR-005 : Project Service avec cache mémoire pour l'index interne

**Documentation créée :**
- `docs/todo.md` — liste vivante des tâches
- `docs/runlog.md` — ce fichier
- `docs/work-breakdown.md` — découpage lots
- `docs/architecture.md` — architecture cible
- `docs/testing.md` — stratégie de test
- `docs/intellij-platform-notes.md` — notes APIs IntelliJ
- `docs/play1-assumptions.md` — hypothèses Play 1
- `docs/adr/` — 5 ADRs initiaux

### Prochaine étape
Lot 1 : Bootstrap du projet Gradle/Kotlin avec IntelliJ Platform Gradle Plugin 2.x.

---

_Template pour les prochaines entrées :_

```
## YYYY-MM-DD — Lot N : Titre

### Contexte
...

### Actions réalisées
- ...

### Problèmes rencontrés
- ...

### Décisions prises
- ...

### Tests lancés
- ...

### Prochaine étape
...
```
