# Gestion des scripts de déploiement et navigation par catégories — plan

> **Pour Hermes :** exécuter les tâches ci-dessous avec tests RED/GREEN, revue et validation sans push.

**But :** éditer le texte réel de déploiement par site, gérer des modèles globaux locaux et appliquer un modèle aux sites cochés, avec authentification à la validation et navigation site par catégories plutôt qu'une suite de boutons.

**Architecture :** endpoints Ploi documentés GET/PATCH `/servers/{server}/sites/{site}/deploy/script` pour la lecture et la mutation ; Ploi ne documente pas d'endpoint global : modèles locaux chiffrés, distribution séquentielle explicite par site et compte rendu par site. Un seul contrôle PIN/biométrique frais au moment de la sauvegarde/du lancement ; aucun bouton d'authentification à part. Navigation par cartes/listes avec écran catégorie borné et retour.

**Tech :** Kotlin, Jetpack Compose, `ProfilePrefs` + `KeystoreTokenCipher`, PloiApi existant, JUnit, Gradle.

---

1. **Modèle et tests** (`DeployScriptTemplates.kt`, `DeployScriptTemplatesTest.kt`) : valider nom/texte/limites, isoler par profil, chiffrer localement, CRUD des modèles ; refuser doublons/corruption, supprimer les données avec le profil. RED : tests ciblés sans implémentation ; GREEN : stockage et tests.
2. **Distribution et tests** (`DeployScriptBatch.kt`, `DeployScriptBatchTest.kt`) : exiger des cibles explicites et uniques, prélecture de toutes les cibles, PATCH séquentiel puis GET de contrôle, collecter succès/échec par site, suspendre le lot sur 429 et ne jamais appeler `/deploy`. RED/GREEN ciblé avec gateway simulée et aucun serveur réel.
3. **Éditeur site** (`DeploymentsScreen.kt`, ressources FR/EN) : agrandir le texte éditable (multiligne, monospace, scroll/IME), conserver le brouillon après erreur ; Enregistrer ouvre le défi PIN/biométrie, aucun appel API avant réussite. Vérifier la liaison par test statique et compilation.
4. **Vue globale** (`GlobalDeployScriptsScreen.kt`, `MainActivity.kt`) : CRUD des modèles, listes paginées serveurs/sites, sélection cochable et persistante, aperçu des cibles, appliquer après unique challenge frais, états de progression et compte rendu partiel exact. Validation tests/UI wiring.
5. **Navigation site** (`SitesScreen.kt`, catégorie FR/EN, tests) : remplacer série de boutons par liste/catégories navigables, en conservant toutes les actions. Travail indépendant délégué, parent inspecte et vérifie.
6. **Intégration** (`SensitiveConfirm.kt`, doc, tests) : déclencher biométrie à l'ouverture du défi et permettre confirmation PIN au clavier, éviter un bouton de validation préalable ; ne pas dégrader verrouillage/rate-limit, mise à jour de la sauvegarde portable si nécessaire. Vérifier `testDebugUnitTest lintDebug assembleDebug`, rapport lint sans warnings, APK propre d'un worktree isolé, commit local sans push. Aucun test destructeur distant sans ressources autorisées.
