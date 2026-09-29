# Contribuer à Ploi Panel

Merci de lire [README.md](README.md), l'[inventaire API](docs/api-coverage.md) et la [politique de sécurité](SECURITY.md) avant une contribution. Le projet n'est pas affilié à Ploi ; ne testez aucune opération d'écriture/suppression contre un compte ou serveur tiers. Les propositions de fonctionnalités doivent distinguer ce qui existe de ce qui est envisagé.

## Préparer une modification

1. Ouvrir une issue descriptive (hors vulnérabilité : voir [SECURITY.md](SECURITY.md)). Ne jamais y joindre jeton Ploi, identifiant personnel, archive, clé privée, capture non expurgée ou fichier de signature.
2. Utiliser JDK 17 et Android SDK API 36 ; travailler sur une branche dédiée. Écrire des tests ciblés pour les corrections et changements de comportement ; simuler les réponses réseau plutôt que contacter une ressource réelle.
3. Exécuter `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Signaler clairement les échecs ou l'absence de tests sur appareil. Pour un changement UI/widget/Keystore, décrire aussi les vérifications manuelles nécessaires.
4. Proposer une pull request avec problème, solution, risques (jetons, permissions, actions destructrices), tests exécutés et documentation adaptée. Ne pas modifier l'inventaire des routes sans source officielle [developers.ploi.io](https://developers.ploi.io/) et preuve locale ; distinguer tests simulés et intégration réelle.

Le code est sous [GNU LGPL-3.0-only](LICENSE) ; en contribuant, vous acceptez la distribution de votre apport sous cette licence. Respecter les licences propres aux dépendances et conserver les mentions applicables. Les changements de licence ou de signature nécessitent une discussion explicite, pas une modification implicite.
