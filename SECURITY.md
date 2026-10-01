# Sécurité

Ploi Panel 0.1.0 gère des jetons API et peut déclencher des opérations sensibles sur des serveurs. La [portée et les limites](README.md#non-inclus--à-valider) doivent être prises en compte : les tests locaux ne prouvent pas une validation en conditions réelles.

## Signaler une vulnérabilité

**N'ouvrez pas d'issue publique** contenant une vulnérabilité exploitable, un jeton, une clé privée, une archive ou des données personnelles. Utilisez la fonction **« Report a vulnerability »** dans l'onglet **Security → Advisories** du [dépôt GitHub](https://github.com/QrCommunication/Ploi-Panel/security/advisories/new) si elle est disponible. Si elle ne l'est pas, demandez dans une issue **sans détail exploitable ni secret** quel canal privé utiliser ; ne publiez pas le rapport tant qu'un canal sûr n'est pas établi. Aucun délai de réponse ou programme de récompense n'est garanti.

Dans un canal privé, indiquer version, étapes minimales de reproduction, impact et correctif éventuel ; remplacer les secrets par des valeurs factices. Ne pas tester sur infrastructure tierce sans autorisation écrite. Une faille de l'API Ploi ou du service Ploi relève aussi des canaux de sécurité de cet opérateur.

## Mesures et responsabilités

Jetons chiffrés via Android Keystore, PIN et biométrie optionnelle ; export chiffré par phrase de passe, sauvegarde Android désactivée. Choisir des scopes API minimaux, protéger et renouveler les jetons compromis, vérifier la provenance et la signature des APK, garder secrets les clés de signature et phrases de passe. Les URL des contrôles HTTP sont contactées par le téléphone. La sonde SSH vérifie une clé d'hôte, mais n'ouvre **aucune session authentifiée**. La compromission de l'appareil ou du fichier d'archive **avec sa phrase de passe** dépasse les protections de l'application. Voir la [notice de confidentialité](docs/PRIVACY.md).
