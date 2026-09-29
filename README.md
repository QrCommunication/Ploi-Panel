# Ploi Panel — administration Ploi sur Android

**Ploi Panel 0.1.0** est un client Android open source, local-first, pour gérer plusieurs profils Ploi, leurs serveurs et leurs sites depuis un téléphone ou un écran pliable. Il communique directement avec l'[API officielle Ploi](https://developers.ploi.io/) ; aucun compte intermédiaire ni serveur Ploi Panel n'est requis. L'application n'est pas affiliée à Ploi.

> **État de la version :** le code et les tests locaux ne prouvent pas une intégration validée sur un compte Ploi réel, ni une publication sur Google Play. Vérifiez les droits du jeton, l'abonnement et les effets des actions avant toute utilisation sur des ressources importantes. Ne publiez jamais de jeton, archive ou clé SSH dans une issue.

## Fonctions présentes en 0.1.0

- Profils Ploi multiples avec jetons Bearer locaux ; navigation serveurs, sites et domaines de l'API, dont déploiements, bases, certificats, sauvegardes et opérations d'administration. Certaines actions modifient ou suppriment des ressources distantes : elles nécessitent une attention particulière. L'[inventaire des routes et de leur statut](docs/api-coverage.md) recense **231 opérations implémentées/testées localement, non vérifiées en conditions réelles** ; il ne garantit ni disponibilité pour chaque compte ni compatibilité permanente avec l'API.
- Interface français/anglais, thème clair/sombre, navigation adaptative ; PIN de l'application, biométrie forte optionnelle et confirmation des opérations sensibles.
- Mesures de monitoring serveur issues de Ploi lorsqu'elles sont disponibles (selon installation et abonnement), avec horodatage ; pas de mesure inventée en l'absence de données.
- Trois types de [widgets d'écran d'accueil](docs/widget-multiserver.md) : **un serveur** (jauges), **jusqu'à quatre serveurs** (liste), et [contrôles HTTP(S) de sites](docs/local-checks.md) (jusqu'à dix cibles). Après création du PIN et d'un profil, maintenir un espace vide sur l'écran d'accueil → **Widgets → Ploi Panel**, puis configurer le widget. Les données peuvent être périmées ; le contenu sensible est masqué lorsque l'appareil est verrouillé.
- [Vérifications locales de sites](docs/local-checks.md) par HTTP(S), indépendantes des moniteurs de l'API Ploi : cibles, codes acceptés, état/latence et alertes panne/rétablissement facultatives. WorkManager a un plancher de **15 minutes**, pas une fréquence garantie ; hors ligne, appareil éteint ou restrictions Android empêchent les alertes.
- [Export/import chiffré de configuration](docs/portable-configuration.md) avec phrase de passe distincte du PIN via sélecteur de fichiers Android. L'archive contient profils, jetons, modèles locaux de scripts et préférences langue/thème ; elle **n'inclut pas** PIN, widgets, historiques, contrôles HTTP locaux, clés privées SSH ni hôtes épinglés. La restauration sur un second appareil reste à vérifier physiquement.
- [Gestion SSH limitée](docs/ssh.md) : import de clés, confiance/empreintes d'hôtes et sonde de clé d'hôte par poignée de main ; **aucun terminal SSH, aucune authentification SSH ni commande distante**.

## Non inclus / à valider

Pas d'OCR de jeton ni d'agent de surveillance externe ; pas de garantie de ping ou notification à intervalle fixe, ni de surveillance si le téléphone est indisponible. La sonde SSH ne remplace pas un client SSH. Les tests JVM, lint et builds ne remplacent pas des essais sur appareils, comptes Ploi dédiés, widgets de lanceur et migrations réelles. Le [cahier produit](docs/product.md) et le [plan historique](docs/implementation.md) décrivent aussi des objectifs, **pas des fonctions promises dans cette version**.

## Installer

- **APK hors Play** : lorsqu'un APK de release signé est disponible dans les [releases GitHub](https://github.com/QrCommunication/Ploi-Panel/releases), vérifier sa provenance et son empreinte publiée avant installation ; autoriser temporairement l'installation depuis cette source dans Android. Ne pas utiliser l'APK `debug` comme version distribuée. Un APK signé avec une clé différente de celle de Google Play peut ne pas permettre une mise à jour directe depuis/vers Play : sauvegarder la configuration chiffrée et envisager une réinstallation.
- **Google Play** : seulement si une fiche officielle est effectivement publiée. À ce stade, ne pas supposer qu'une fiche Play existe ; consulter les releases ou ce dépôt. Procédure de préparation : [publication Play](docs/PLAY_RELEASE.md). Le Play Store requiert un **AAB** pour les nouvelles applications ; un APK GitHub ne suffit pas.
- Android **10+ (API 29)** ; version cible Android **16 (API 36)**. Créer un jeton API dans Ploi avec les scopes nécessaires, puis ajouter le profil dans l'application ; selon le compte, certaines opérations et mesures seront indisponibles.

## Architecture, sécurité et données

Kotlin, Jetpack Compose, client API Ploi direct en HTTPS, préférences privées et chiffrement des jetons par Android Keystore ; widgets et tâches WorkManager lisent des données locales et se rafraîchissent au mieux. Les requêtes API envoient le jeton à Ploi ; les contrôles HTTP contactent les URL configurées (et peuvent exposer l'IP du téléphone aux sites ciblés). Pas de backend ni de télémétrie intégrés au projet. Sauvegarde système Android désactivée ; export volontaire chiffré. Lire la [politique de confidentialité](docs/PRIVACY.md), la [politique de sécurité](SECURITY.md) et la [portabilité](docs/portable-configuration.md). Le chiffrement local ne protège pas d'un appareil déjà compromis ni d'une archive partagée avec sa phrase de passe.

## Construire et vérifier

Prérequis : **JDK 17**, SDK Android API 36 installé et licences SDK acceptées. Depuis la racine du dépôt :

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK de développement : `app/build/outputs/apk/debug/app-debug.apk`. Tests unitaires/API avec réponses simulées, sans compte Ploi réel. Une distribution de release exige signature privée hors dépôt, tests sur appareil et revue des politiques Play ; voir [PLAY_RELEASE.md](docs/PLAY_RELEASE.md). Contributions : [CONTRIBUTING.md](CONTRIBUTING.md) ; changements : [CHANGELOG.md](CHANGELOG.md).

## FAQ

**L'application est-elle officielle ?** Non : client communautaire indépendant, non affilié à Ploi. Référence API : [developers.ploi.io](https://developers.ploi.io/).

**Le monitoring fonctionne-t-il téléphone éteint ?** Non. WorkManager est best effort ; une supervision continue exige une infrastructure externe non fournie ici.

**Une sauvegarde inclut-elle mes widgets et clés SSH ?** Non. Voir la [liste exacte des données](docs/portable-configuration.md).

**Puis-je ouvrir un terminal SSH ?** Non : seul le contrôle de clé d'hôte est présent.

**Puis-je installer la même version depuis Play après l'APK GitHub ?** Cela dépend de la clé de signature de l'APK et de celle utilisée par Play ; une réinstallation peut être nécessaire. Ne pas présumer d'une migration transparente.

## Licence

Code de ce dépôt : **GNU LGPL-3.0-only**, voir [LICENSE](LICENSE) (permissions additionnelles à la [GPL-3.0](LICENSES/GPL-3.0.txt)). Les dépendances et services tiers conservent leurs propres licences. « Ploi » appartient à ses titulaires respectifs.
