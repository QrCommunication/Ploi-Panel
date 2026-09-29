# Journal des changements

Le journal suit les versions de Ploi Panel. La présence d'une version dans le code ou dans ce fichier ne signifie pas qu'elle est publiée sur Google Play. Ne pas confondre tests locaux et validation sur compte Ploi réel.

## [0.1.0] — préversion GitHub

- Client Android natif Kotlin/Compose, profils Ploi multiples et interface FR/EN avec PIN et biométrie optionnelle.
- Client des opérations de l'API Ploi inventoriées dans [docs/api-coverage.md](docs/api-coverage.md), testé localement avec réponses simulées, non validé sur compte réel.
- Widgets serveur, jusqu'à quatre serveurs et vérifications HTTP(S) locales des sites ; WorkManager au mieux, minimum programmé de 15 minutes sans garantie de fréquence.
- Export/import de configuration chiffrée, modèles locaux de scripts ; coffre de clés SSH et sonde d'empreinte d'hôte, sans terminal SSH.
- Documentation de publication Play, confidentialité, contribution et sécurité ; licence du dépôt déclarée **LGPL-3.0-only**, texte de la GPLv3 conservé comme référence juridique dans `LICENSES/`.

**Non vérifié pour cette entrée :** publication Play, tests complets sur appareil, intégration Ploi en conditions réelles et restauration physique interappareils. Voir [README](README.md) et [procédure Play](docs/PLAY_RELEASE.md).
