# Journal des changements

Le journal suit les versions de Ploi Panel. La présence d'une version dans le code ou dans ce fichier ne signifie pas qu'elle est publiée sur Google Play. Ne pas confondre tests locaux et validation sur compte Ploi réel.

## [0.3.0] — refonte de l'interface et corrections vérifiées sur un compte réel

Interface entièrement revue et parcourue sur un compte Ploi réel en lecture seule, rendue sur la JVM (Robolectric, graphismes natifs) en quatre formats : téléphone, pliant fermé (344 dp), pliant ouvert (690 dp) et tablette paysage (1280 dp).

**Corrections de bugs constatés sur des données réelles**

- Le widget « un serveur » ne pouvait pas s'afficher sur un appareil : sa mise en page utilisait une vue `View`, interdite dans `RemoteViews` (le lanceur affiche « Impossible de charger le widget »). Présent dans les APK 0.2.0 non publiés. Un test (`RemoteViewsWhitelistTest`) vérifie désormais les classes utilisées par toutes les mises en page de widgets.
- Écran Projets en erreur « Réponse Ploi illisible » : `/projects` renvoie les serveurs sous forme d'objets `{id, name, status, ip}` et non la liste d'identifiants décrite dans la documentation. Les deux formes sont acceptées et les noms de serveurs sont affichés.
- Vue d'ensemble des serveurs surveillés vide : `/servers/monitored` renvoie des séries nommées (`{"name":"CPU","data":{"HH:mm":valeur}}`). Les séries sont assemblées par horodatage, dans un ordre qui reste correct autour de minuit ; une mesure nulle reste inconnue au lieu d'être lue comme zéro.
- Horodatages de monitoring : `/monitor` envoie `yyyy-MM-dd HH:mm:ss` sans fuseau (UTC). Les dates étaient affichées brutes, décalées, et toutes les mesures étaient considérées comme périmées ; elles sont désormais converties dans le fuseau et le format de l'appareil.
- Les 35 lectures de l'API ont été rejouées sur les réponses réelles du compte (`LiveParserAudit`).

**Interface**

- Design system appliqué à tous les écrans ([docs/design-system.md](docs/design-system.md)) : barre d'outils, cartes de ressources avec statut en mots, détails en paires libellé/valeur, actions destructrices distinguées en rouge et toujours derrière leur confirmation, bandeaux de succès, états vide/erreur/chargement homogènes, valeurs machine en police à largeur fixe, sorties longues repliées.
- Liste des serveurs : 50 serveurs par page (maximum documenté), filtres par état avec compteurs de la page chargée, recherche locale dont la portée est indiquée, tirer pour actualiser, bouton d'action flottant. Les nouveaux tests des serveurs injoignables sont résumés sur une ligne dans la liste, le détail complet restant sur la fiche.
- Statuts, priorités et règles de pare-feu affichés en mots traduits (« Injoignable », « À traiter », « Autoriser »…) ; les valeurs inconnues restent visibles telles qu'envoyées.
- Formats : libellés de navigation raccourcis pour les écrans étroits, rail latéral à partir de 600 dp, liste et détail côte à côte à partir de 720 dp.
- Widgets : pied de carte compact tenant en 2×2, statut Ploi précis, jauges natives colorées par palier.

**Vérifications** : 751 tests JVM, 0 échec ; rapport lint `No issues found.` ; APK release signé par la clé d'upload existante (`versionCode` 3, met à jour 0.1.0/0.2.0).

**Non vérifié :** usage sur appareil réel (gestes, claviers tiers, TalkBack, biométrie, tailles de texte système), widgets posés sur un écran d'accueil, terminal SSH connecté à un serveur, et toutes les actions qui créent, modifient ou suppriment des ressources Ploi — le parcours automatisé est strictement en lecture.

## [0.2.0] — terminal SSH et alertes

- **Terminal SSH interactif** : sessions multiples en onglets, émulateur xterm-256color intégré (UTF-8, couleurs 16/256/truecolor, écran alternatif, régions de défilement, graphismes DEC, curseur applicatif, collage entre crochets), barre de touches (Ctrl/Alt collants, Échap, Tab, flèches, Home/End, PgUp/PgDn, F1–F12), clavier physique, copier/coller, police réglable, historique de 2 000 lignes.
- Authentification par mot de passe (non enregistré) ou clé ; clés protégées par phrase de passe désormais importables (demandée à chaque connexion) ; clé publique déduite du PEM ; **génération Ed25519 sur l'appareil** et autorisation sur un serveur via `POST /servers/{server}/ssh-keys` derrière confirmation PIN/biométrie.
- Vérification stricte de la clé d'hôte contre les épingles TOFU **avant authentification** ; hôte inconnu ou modifié = blocage, jamais d'épinglage implicite. Transfert d'agent/X11 désactivé. Bouton « Ouvrir un terminal SSH » sur chaque serveur (IP et port SSH Ploi préremplis), destinations mémorisées par profil, sessions fermées à la suppression du profil.
- **Alertes de seuil** CPU/RAM/disque par serveur (Ploi Monitoring), déclenchement sur N mesures consécutives, hystérésis de 3 points, notification de rétablissement.
- **Verrouillage automatique configurable** (immédiat par défaut, jusqu'à 15 min, horloge monotone).
- **Lecture hors ligne** : liste des serveurs servie depuis un cache local chiffré quand Ploi est injoignable, avec horodatage et actions désactivées ([docs/offline-cache.md](docs/offline-cache.md)).
- Annonces lecteur d'écran pour indicateurs de chargement et graphiques de monitoring.
- Bouncy Castle ajouté pour Ed25519/X25519 sur Android 10–12. Tests de bout en bout contre un serveur SSH embarqué (Apache MINA SSHD) : hôte inconnu, clé modifiée, mot de passe, clé Ed25519 générée, PTY et redimensionnement.
- `versionCode` 2 : l'APK met à jour 0.1.0 signé avec la même clé. L'asset de preuve ADI reste inclus. **Non publiée sur GitHub** : son contenu est inclus dans 0.3.0.

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
