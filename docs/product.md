# Cahier produit — Ploi Panel

## Décisions validées dans le thread Telegram 5929

- Publication Play Store envisagée, dépôt public dans `QrCommunication/Ploi-Panel`, licence GPL-3.0.
- Android natif : Kotlin, Jetpack Compose, layouts adaptatifs pour téléphones et appareils pliables, en particulier Fold 8 et Fold 8 Ultra. La largeur disponible et la posture, non le nom commercial de l'appareil, déterminent les layouts. Version Android minimale à fixer selon tests de compatibilité et distribution Play ; viser au moins les appareils des trois dernières années.
- Français et anglais ; thème clair/sombre automatique et sélection manuelle.
- Plusieurs profils Ploi isolés, jeton saisi ou lu via photo/OCR ; photo traitée localement puis supprimée sans sauvegarde. Ne jamais exposer les jetons au presse-papiers, aux logs, aux sauvegardes Android non chiffrées ni aux widgets.
- Onboarding : code d'application obligatoire et activation biométrique demandée ; verrouillage automatique avec durée configurable. Cas sans capteur, matériel temporairement indisponible et restauration sur nouvel appareil à définir explicitement, sans contournement silencieux du code.
- Couverture exhaustive des endpoints documentés de l'API Ploi : opérations de lecture, création, modification et suppression, ainsi que les commandes spécifiques aux ressources. Actions destructrices ou sensibles après vérification par code ou biométrie, même si l'application est déjà déverrouillée.
- Sources de prestataires et offres/regions depuis l'API Ploi, avec capacités réellement disponibles par profil. Si l'API ne permet pas d'ajouter un prestataire, proposer un chemin externe explicite et ne pas simuler cette capacité. Les fournisseurs non actifs sont visibles mais non sélectionnables jusqu'à configuration.
- Ploi Monitoring en source primaire pour CPU, RAM, disque et charge ; agent auto-hébergé optionnel uniquement pour ce que l'API ne fournit pas, avec consentement explicite. Pas d'agent requis pour utiliser l'application.
- Widgets : un récapitulatif adaptable multi-serveurs et autant de widgets individuels que désiré ; sélection des serveurs/sites et des métriques, affichage d'un horodatage et d'un état « données périmées ».
- Vérification du site depuis le téléphone : disponibilité via HTTP(S), résultat et latence, pas ICMP implicite. URL et timeout configurables ; tenir compte des redirections, TLS, certificats, codes acceptables, faux positifs transitoires et réseaux captifs. Notifications panne/rétablissement et seuils, réglables par profil et ressource. Les limites Android imposent des contrôles d'arrière-plan *best effort*, pas de garantie stricte à 5 min.
- SSH direct téléphone → serveur, avec vérification de clé hôte, gestion de clés sécurisée et interdiction de désactiver silencieusement la vérification.
- Tout local : pas de backend propriétaire ni de compte intermédiaire. Cache local en lecture hors ligne, export/import chiffré avec phrase de passe distincte de la clé matérielle Android ; migration interappareils vérifiée. Désactiver Android Auto Backup pour les secrets.
- Pagination adaptée et taille personnalisable. L'API Ploi documente `per_page` jusqu'à 50 ; une préférence supérieure doit être réalisée en pagination côté client, pas envoyée telle quelle.

## Frontières de faisabilité constatées

- L'authentification API documentée repose sur un jeton Bearer créé dans le profil Ploi. L'application ne doit pas automatiser la connexion web ni récupérer les identifiants du compte Ploi.
- L'API de monitoring documentée fournit CPU, RAM, disque, load average, horodatage ; elle requiert installation et abonnement suffisant. Pas de ping de site dans cette réponse API.
- La création de serveur requiert notamment le triplet plan/région/credential d'un prestataire déjà associé au compte ; l'API expose les plans et régions d'un prestataire. Les fonctionnalités qui ne sont pas documentées ne doivent pas être promises comme intégration native sans validation.
- Limites API documentées : Basic 60, Pro 120, Unlimited 240 requêtes/minute. Utiliser les en-têtes `X-RateLimit-*` et `Retry-After`, mutualiser les rafraîchissements entre UI et widgets, gérer 401/403/429, pagination et tâches asynchrones.
- L'appli ne peut envoyer d'alerte si le téléphone est hors ligne, arrêté, privé de notifications ou fortement restreint en arrière-plan. Une surveillance garantie nécessite une infrastructure externe ; l'utilisateur privilégie expressément le tout-local.

## Sécurité et validation

- Séparer état applicatif, contenu cache et secrets ; Android Keystore pour les clés locales, chiffrement authentifié et dérivation de mot de passe résistante pour l'archive portable, avec version de format et tests de corruption/mauvaise phrase de passe.
- Pas de stockage des jetons dans les URI, les logs, les crash reports ou les historiques d'actions. Captures masquées sur vues sensibles ; permissions minimales et demandées au moment du besoin.
- Les opérations API doivent être testées contre la documentation puis, sur ressources de test dédiées, contre un compte réel avec autorisation explicite avant les suppressions.
- Tests unitaires, tests d'intégration avec serveur HTTP simulé, tests UI téléphones/écrans dépliés/repliés, tests de restauration interappareils et tests d'autonomie/réseaux intermittents.

## Références officielles

- [API Ploi](https://developers.ploi.io/)
- [Authentification](https://developers.ploi.io/getting-started/authorization)
- [Monitoring](https://developers.ploi.io/servers/monitoring)
- [Création de serveur](https://developers.ploi.io/servers/create-server)
- [Prestataire](https://developers.ploi.io/user/get-server-provider)
- [Pagination](https://developers.ploi.io/getting-started/pagination)
- [Rate limiting](https://developers.ploi.io/getting-started/rate-limiting)
