# Supervision locale des sites (watchdog)

## Portée

Contrôles HTTP(S) exécutés par le téléphone lui-même, indépendants de l'API Ploi et distincts
des moniteurs Ploi (domaine `monitoring`, lecture seule côté API). Aucune donnée ne quitte
l'appareil : les cibles (libellé, URL, méthode, délai, codes acceptés, débordement) et le
dernier état connu vivent dans les préférences locales, sans secret.

## Comportement

- **Noyau pur JVM** (`LocalSiteCheck.kt`) : validation des cibles, persistance JSON, moteur
  d'évaluation avec débordement (une cible ne bascule DOWN qu'après N échecs consécutifs ;
  un succès réinitialise immédiatement le compteur). Redirections non suivies : le code
  observé est rapporté tel quel et l'opérateur choisit les codes acceptés.
- **Planification au mieux** (`LocalCheckWorker.kt`) : WorkManager périodique, plancher
  Android de 15 minutes, contrainte réseau. Aucune garantie : pas d'alerte possible si
  l'appareil est hors ligne, éteint, restreint en arrière-plan ou si les notifications sont
  refusées. Le travail périodique est annulé dès qu'il n'y a plus de cible.
- **Alertes opt-in** : bascule « Alertes panne / rétablissement » dans l'écran Supervision,
  désactivée par défaut ; permission `POST_NOTIFICATIONS` demandée au moment de l'activation
  (API 33+). Une notification part uniquement sur les fronts réels (→ DOWN après débordement,
  DOWN → UP au rétablissement) ; les échecs répétés à l'état DOWN ne renotifient pas.
- **UI** (onglet Supervision) : liste des cibles avec état, latence, code HTTP, date de
  dernière vérification et marqueur « périmé » au-delà de 30 minutes ; vérification manuelle
  unitaire ou globale ; ajout/édition avec le formulaire conservé ouvert en cas d'erreur ;
  suppression avec confirmation. L'onglet est actuellement placé derrière la sélection d'un
  profil actif comme les autres onglets, bien que les contrôles n'utilisent pas l'API Ploi.

## Tests

`LocalSiteCheckTest` (JVM) couvre validation, limites, doublons, persistance/corruption,
transitions du moteur, parsing des codes acceptés, préférence d'alertes et fronts de
notification. L'affichage réel (canal de notification, permission, planification WorkManager)
n'est pas validé ici faute d'émulateur : lint + tests unitaires + build seulement.
