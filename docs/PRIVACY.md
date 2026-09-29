# Confidentialité — Ploi Panel 0.1.0

Cette notice décrit le comportement du **code de cette version**, pas celui des services externes ni une déclaration Play Console déjà approuvée. Ploi Panel est un client local-first sans compte intermédiaire ni serveur propriétaire de télémétrie déclaré dans le projet. **Local-first ne signifie pas absence de trafic réseau.** Pour des questions de confidentialité ou un signalement sensible, voir [SECURITY.md](../SECURITY.md).

## Données et destinataires

| Données | Traitement et destination |
|---|---|
| Libellés de profils, jetons API Ploi | Saisis par l'utilisateur ; libellés dans les préférences privées, jetons chiffrés au repos avec Android Keystore. Le jeton est envoyé à **Ploi** dans les requêtes API HTTPS Bearer ; Ploi peut voir le compte, les ressources consultées et les actions demandées. Consulter les conditions de confidentialité de Ploi sur son site officiel pour son propre traitement. |
| Serveurs, sites, métriques et paramètres de l'application | Obtenus de l'API Ploi et/ou conservés localement pour l'interface et les widgets. Des libellés, horodatages et états non secrets peuvent être présents dans les préférences privées ; tout le magasin local n'est **pas** chiffré champ par champ. Les actions volontaires sur les ressources sont envoyées à Ploi. |
| Cibles de contrôle HTTP(S) | Libellé, URL, paramètres et dernier état conservés localement. Chaque vérification manuelle ou WorkManager **contacte l'URL choisie** : le serveur cible et ses intermédiaires peuvent recevoir IP, chemin URL, heure et métadonnées de la requête. Choisir des URL sans secrets en paramètre. Les cibles ne sont pas des « données qui ne quittent jamais l'appareil » lors d'un contrôle. |
| Clés privées SSH importées et empreintes d'hôtes | Clés privées chiffrées localement avec Keystore ; empreintes/hôtes conservés dans les préférences privées. La sonde SSH ouvre une poignée de main vers l'hôte configuré (qui voit l'IP/heure) sans identifiant ni commande. Pas de terminal SSH en 0.1.0. |
| PIN/biométrie | PIN géré localement ; biométrie optionnelle via Android. La biométrie est vérifiée par Android ; l'application ne demande pas vos empreintes brutes. |
| Archives exportées | Export **volontaire**, chiffré par phrase de passe et enregistré à l'emplacement choisi dans le sélecteur Android. L'archive inclut les jetons : la personne possédant fichier **et** phrase de passe peut les récupérer. Import local ; pas de serveur Ploi Panel pour restaurer. Contenu exact et exclusions : [portabilité](portable-configuration.md). |

## Arrière-plan, notifications, sauvegarde

WorkManager peut rafraîchir des mesures Ploi et effectuer les contrôles HTTP(S) configurés lorsque le réseau est disponible ; périodicité minimale programmée de 15 minutes, exécution **non garantie**. Les alertes locales panne/rétablissement sont désactivées par défaut ; sur Android 13+, la permission `POST_NOTIFICATIONS` est demandée lors de l'activation. Refuser la permission empêche les notifications, pas les contrôles configurés. Les widgets affichent les derniers états/mesures disponibles, éventuellement périmés, et masquent leurs données sensibles sur écran verrouillé selon le comportement du lanceur/Android.

La sauvegarde automatique Android et le transfert de données système sont exclus par le manifeste et les règles d'extraction du projet ; l'export chiffré manuel reste possible. Les archives confiées à une application de fichiers ou un fournisseur cloud choisi par l'utilisateur relèvent aussi de leurs propres politiques. La suppression d'un profil efface ses secrets locaux associés ; les copies d'archives déjà exportées et les informations détenues par Ploi ou des sites contactés ne sont pas effacées par cette action. Désinstaller l'application supprime normalement ses données locales selon les règles Android, pas les données chez Ploi.

Aucun SDK publicitaire, outil d'analyse d'usage ni remontée automatique de crash n'est déclaré dans les dépendances applicatives de cette version ; ne pas en déduire l'absence de journaux réseau chez Ploi, les sites consultés, Android ou le fournisseur d'accès. Aucun engagement de surveillance permanente ni de sécurité absolue. Revoir cette notice et la déclaration **Data safety** de Play avant toute publication ou ajout de dépendance/collecte.

Sources : [API Ploi](https://developers.ploi.io/), [README](../README.md), [sécurité](../SECURITY.md).
