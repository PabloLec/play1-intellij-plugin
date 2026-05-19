# ADR-005 — Index interne via Project Service (cache mémoire)

**Date :** 2026-05-19  
**Statut :** accepted

---

## Contexte

Le plugin doit maintenir un modèle interne du projet Play 1 : liste des routes, controllers, views, diagnostics. Ce modèle est utilisé par la Tool Window, les inspections, la complétion et la navigation.

Deux approches principales existent dans l'écosystème IntelliJ :

1. **Cache mémoire dans un Project Service** — simple, rapide à implémenter, non persistant entre sessions
2. **FileBasedIndex** — index persistant sur disque, rafraîchi par IntelliJ automatiquement, plus complexe

---

## Décision

Pour le MVP, utiliser un **Project Service avec cache mémoire** (`Play1ProjectService`).

L'index `FileBasedIndex` pourra remplacer ou compléter cette approche dans une version future.

---

## Raisonnement

Le `FileBasedIndex` est plus puissant mais significativement plus complexe à implémenter correctement :
- Nécessite des stubs `DataIndexer`, `FileBasedIndex.Extension`, gestion des invalidations
- Complexité hors de proportion pour un MVP dont la priorité est d'être stable et utile rapidement

Un cache mémoire dans un Project Service est :
- Simple à implémenter et à tester
- Suffisamment performant pour des projets Play 1 (moins de 1000 routes en général)
- Facile à invalider manuellement (bouton Refresh, listener VFS)

---

## Stratégie de rafraîchissement du cache

Le cache est rafraîchi :
1. Au démarrage du projet (via `Play1StartupActivity`)
2. Quand `conf/routes` est modifié (via `VirtualFileListener`)
3. Quand un fichier dans `app/controllers/` est créé ou supprimé
4. Via le bouton "Refresh Index" de la Tool Window

---

## Alternatives considérées

| Option | Raison du rejet |
|--------|----------------|
| `FileBasedIndex` d'emblée | Trop complexe pour le MVP, à envisager en phase 2+ |
| Scan synchrone à chaque accès | Trop lent pour la complétion (latence UI inacceptable) |
| Pas d'index du tout | Certaines features (Tool Window, diagnostics) sont impossibles sans état |

---

## Conséquences

- `Play1ProjectService` est un `@Service(Service.Level.PROJECT)` avec un objet compagnon `getInstance(project)`
- Il expose : `isPlay1Project`, `routes`, `controllers`, `views`, `diagnostics`
- Le service est thread-safe (lecture depuis n'importe quel thread, write actions wrappées)
- Les données ne survivent pas à un redémarrage IDE — le scan s'exécute à nouveau à chaque ouverture (acceptable)
- La migration vers `FileBasedIndex` est documentée comme évolution future dans `docs/work-breakdown.md`

---

## Références

- [Project Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)
- [File Based Index](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html)
