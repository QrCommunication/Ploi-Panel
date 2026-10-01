# Lecture hors ligne (cache local chiffré)

Ce document décrit ce que l'application conserve localement pour rester **lisible** quand Ploi est
injoignable, et surtout ce qu'elle **ne fait pas**. Il complète
[l'inventaire API](api-coverage.md) et la [configuration portable](portable-configuration.md).

## Ce qui est implémenté

- **`OfflineCache`** (`OfflineCache.kt`) : cache en lecture seule des pages de serveurs, **par
  profil**. Le conteneur entier (noms, adresses IP, statuts) est chiffré via le même mécanisme
  AES-GCM adossé au Keystore que les jetons, sous un **alias dédié** `ploi-panel.offline-cache`,
  distinct des jetons Ploi, des modèles de déploiement et des clés SSH.
- **Écriture** : chaque lecture réseau réussie d'une page de serveurs met la page à jour dans le
  cache. L'échec d'écriture du cache est avalé : il ne doit jamais casser une lecture réussie.
- **Lecture** : le cache n'est servi **que** lorsque la lecture échoue pour cause de
  **connectivité** (`PloiOfflineException`). Une réponse HTTP — 401 jeton invalide, 403 scope
  manquant, 429 limite atteinte, 5xx, charge utile illisible — signifie que Ploi a été joint et
  qu'il nous dit quelque chose : masquer cela derrière des lignes périmées cacherait un vrai
  problème de compte. La décision est isolée dans `shouldServeCache` et testée pour chaque cas.
- **L'erreur reste affichée** : la bannière de cache s'ajoute au message d'erreur, elle ne le
  remplace pas.
- **Bornes** :
  - au plus **8 pages** par profil, éviction de la plus ancienne lecture d'abord (départage
    déterministe par numéro de page) ;
  - au plus **50 lignes** par page, la limite documentée de Ploi ; au-delà, l'écriture est refusée ;
  - métadonnées de pagination incohérentes ou IDs dupliqués : écriture refusée, rien n'est caché ;
  - entrée de plus de **24 h** : non servie ;
  - entrée horodatée **dans le futur** de plus de 5 minutes (changement d'horloge, altération) :
    non servie.
- **Échec fermé, sans destruction** : un conteneur illisible (mauvaise clé d'appareil, corruption,
  altération, version inconnue) se comporte comme un cache vide ; la valeur stockée est
  **délibérément laissée en place** afin qu'une défaillance transitoire du Keystore ne détruise pas
  les données de l'utilisateur.
- **Suppression de profil** : `ProfileStore.remove` efface le cache du profil, comme son jeton, ses
  modèles de déploiement et son matériel SSH, sans toucher aux autres profils.
- **UI** : quand des lignes proviennent du cache, un bandeau indique en toutes lettres qu'il s'agit
  d'une lecture hors ligne **et l'instant absolu** de l'enregistrement (jamais un « il y a x
  heures » arrondi qui minimiserait l'ancienneté). Le bandeau est annoncé aux lecteurs d'écran comme
  une phrase unique (`clearAndSetSemantics`), la couleur seule ne disant rien.
- **Lecture seule assumée** : en mode cache, la création de serveur, la vue « serveurs surveillés »
  et **l'ouverture du détail d'un serveur** sont désactivées — ces écrans exigent des appels réseau
  que l'on ne peut pas honorer, et agir sur un état de compte non relu serait dangereux. Seul
  « Recharger » reste actif, afin de pouvoir sortir du mode hors ligne.

## Explicitement NON implémenté

- **Aucune écriture hors ligne** : pas de file d'attente de mutations, pas de rejeu différé. Une
  action qui échoue échoue, visiblement.
- **Aucun autre domaine que les pages de serveurs** n'est caché à ce stade : sites, bases,
  déploiements, monitoring et le reste restent purement en ligne. Les mesures de monitoring des
  widgets ont leur propre cache, décrit dans [widget-multiserver.md](widget-multiserver.md).
- Le cache **n'est pas** une sauvegarde et n'est pas inclus dans l'archive portable : il est
  reconstruit par une simple lecture et reste lié à la clé Keystore de l'appareil.

## Limites de validation

Tests JVM uniquement (persistance, éviction, expiration, dérive d'horloge, altération, clé
étrangère, portée par profil, effacement à la suppression de profil) plus des tests de contrat
statiques sur le câblage de l'écran. Le **Keystore réel**, une véritable coupure réseau et le rendu
du bandeau **n'ont pas été validés sur appareil ni émulateur** : ne pas en conclure que le mode hors
ligne est éprouvé en conditions réelles.
