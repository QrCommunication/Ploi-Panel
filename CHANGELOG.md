# Journal des changements

Le journal suit les versions de Ploi Panel. La présence d'une version dans le code ou dans ce fichier ne signifie pas qu'elle est publiée sur Google Play. Ne pas confondre tests locaux et validation sur compte Ploi réel.

## [0.2.0] — terminal SSH et alertes

- **Terminal SSH interactif** : sessions multiples en onglets, émulateur xterm-256color intégré (UTF-8, couleurs 16/256/truecolor, écran alternatif, régions de défilement, graphismes DEC, curseur applicatif, collage entre crochets), barre de touches (Ctrl/Alt collants, Échap, Tab, flèches, Home/End, PgUp/PgDn, F1–F12), clavier physique, copier/coller, police réglable, historique de 2 000 lignes.
- Authentification par mot de passe (non enregistré) ou clé ; clés protégées par phrase de passe désormais importables (demandée à chaque connexion) ; clé publique déduite du PEM ; **génération Ed25519 sur l'appareil** et autorisation sur un serveur via `POST /servers/{server}/ssh-keys` derrière confirmation PIN/biométrie.
- Vérification stricte de la clé d'hôte contre les épingles TOFU **avant authentification** ; hôte inconnu ou modifié = blocage, jamais d'épinglage implicite. Transfert d'agent/X11 désactivé. Bouton « Ouvrir un terminal SSH » sur chaque serveur (IP et port SSH Ploi préremplis), destinations mémorisées par profil, sessions fermées à la suppression du profil.
- **Alertes de seuil** CPU/RAM/disque par serveur (Ploi Monitoring), déclenchement sur N mesures consécutives, hystérésis de 3 points, notification de rétablissement.
- **Verrouillage automatique configurable** (immédiat par défaut, jusqu'à 15 min, horloge monotone).
- **Lecture hors ligne** : liste des serveurs servie depuis un cache local chiffré quand Ploi est injoignable, avec horodatage et actions désactivées ([docs/offline-cache.md](docs/offline-cache.md)).
- Annonces lecteur d'écran pour indicateurs de chargement et graphiques de monitoring.
- Bouncy Castle ajouté pour Ed25519/X25519 sur Android 10–12. Tests de bout en bout contre un serveur SSH embarqué (Apache MINA SSHD) : hôte inconnu, clé modifiée, mot de passe, clé Ed25519 générée, PTY et redimensionnement.
- `versionCode` 2 : l'APK met à jour 0.1.0 signé avec la même clé. L'asset de preuve ADI reste inclus.

**Non vérifié :** usage sur appareil réel et serveurs Ploi réels, claviers logiciels tiers (IME), rendu sur écrans pliables.

## [0.1.0-adi.1] — APK de vérification de propriété

- Ajout de `assets/adi-registration.properties` contenant l'extrait de vérification fourni pour le compte développeur ; l'APK release signé sert à l'étape **Android developer verification → Importer un APK**, pas à confirmer une publication Play. Même package, versionCode et clé d'upload que 0.1.0. Voir [docs/PLAY_RELEASE.md](docs/PLAY_RELEASE.md).
- La validation du package par Google dépend du certificat sélectionné dans la console et reste à effectuer manuellement ; aucun résultat Play n'est présumé.

## [0.1.0] — préversion GitHub

- Client Android natif Kotlin/Compose, profils Ploi multiples et interface FR/EN avec PIN et biométrie optionnelle.
- Client des opérations de l'API Ploi inventoriées dans [docs/api-coverage.md](docs/api-coverage.md), testé localement avec réponses simulées, non validé sur compte réel.
- Widgets serveur, jusqu'à quatre serveurs et vérifications HTTP(S) locales des sites ; WorkManager au mieux, minimum programmé de 15 minutes sans garantie de fréquence.
- Export/import de configuration chiffrée, modèles locaux de scripts ; coffre de clés SSH et sonde d'empreinte d'hôte, sans terminal SSH.
- Documentation de publication Play, confidentialité, contribution et sécurité ; licence du dépôt déclarée **LGPL-3.0-only**, texte de la GPLv3 conservé comme référence juridique dans `LICENSES/`.

**Non vérifié pour cette entrée :** publication Play, tests complets sur appareil, intégration Ploi en conditions réelles et restauration physique interappareils. Voir [README](README.md) et [procédure Play](docs/PLAY_RELEASE.md).
