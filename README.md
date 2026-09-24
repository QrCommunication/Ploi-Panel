# Ploi Panel

Application Android native et open source pour administrer plusieurs comptes Ploi et leurs serveurs/sites depuis un téléphone ou un appareil pliable.

> **Statut : conception et fondations.** Le dépôt ne contient pas encore d'application utilisable. Voir [le cahier produit](docs/product.md) et [le plan de réalisation](docs/implementation.md). Ne pas saisir de jeton Ploi dans les issues, les commits ou les discussions.

## Principes

- Kotlin / Jetpack Compose, interface adaptative téléphone et Galaxy Z Fold 8 / Fold 8 Ultra.
- Connexion directe à l'API officielle Ploi, données et secrets conservés localement sur l'appareil ; aucun backend obligatoire.
- Plusieurs profils indépendants, interface française et anglaise, thèmes clair et sombre.
- Couverture de **toutes les opérations documentées** de l'API Ploi, sous réserve des scopes du jeton et des capacités de l'abonnement ; fonctionnalités absentes de l'API identifiées et traitées séparément.
- Widgets Android adaptatifs et individuels ; métriques Ploi et surveillance configurable des sites.
- Sauvegarde et restauration chiffrées ; pas de secret en clair ni de télémétrie implicite.

**Limite importante :** un téléphone seul ne peut garantir un ping toutes les cinq minutes ni une alerte lorsque le téléphone est hors ligne ou soumis aux restrictions Android. Les vérifications en arrière-plan sont *best effort*. Une surveillance serveur autonome pourra être proposée plus tard comme composant optionnel, jamais imposé.

## Licence

GPL-3.0. Les forks sont autorisés dans le respect de la licence, notamment la conservation des mentions d'origine et la distribution du code source des versions dérivées distribuées. Ce projet n'est pas affilié à Ploi ; « Ploi » appartient à ses titulaires respectifs.

Documentation API : https://developers.ploi.io/
