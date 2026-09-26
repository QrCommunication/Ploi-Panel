# Ploi Panel

Application Android native et open source pour administrer plusieurs comptes Ploi et leurs serveurs/sites depuis un téléphone ou un appareil pliable.

> **Statut : premier jalon exécutable, non publiable.** L'APK de développement permet de saisir un jeton Ploi **uniquement en mémoire**, lister les serveurs avec pagination, consulter leurs dernières métriques Ploi et parcourir les prestataires associés (offres/régions). Il ne propose pas encore multi-profils persistants, code/biométrie, OCR, SSH, widgets ni les opérations de gestion. Ne pas distribuer l'APK comme version finale. Voir [le cahier produit](docs/product.md) et [le plan de réalisation](docs/implementation.md). Ne pas saisir de jeton Ploi dans les issues, les commits ou les discussions.

## Développement

Prérequis : JDK 17, Android SDK API 36 et licence SDK acceptée. Puis `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. APK généré : `app/build/outputs/apk/debug/app-debug.apk`. Aucune donnée Ploi réelle n'est utilisée par les tests ; l'intégration avec un compte réel reste à valider. La version cible est Android 16 (API 36), la version minimale Android 10 (API 29).

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
