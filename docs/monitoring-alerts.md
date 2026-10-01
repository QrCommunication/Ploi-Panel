# Alertes de seuil de monitoring

Onglet **Monitoring** d'un serveur → carte « Alertes de seuil ». Seuils facultatifs CPU, RAM et
disque (1–100 %), et nombre de mesures consécutives au-dessus du seuil avant d'alerter (1–4).
La charge (*load average*) n'est pas un pourcentage : elle n'a pas de seuil.

## Fonctionnement

- `ThresholdAlertWorker` (WorkManager, périodique **15 minutes minimum**, réseau requis) lit la
  dernière mesure `GET /servers/{server}/monitoring` de chaque serveur surveillé, avec le jeton du
  profil concerné.
- `evaluateThresholds` est pur et testé : notification **une seule fois** au franchissement
  confirmé, puis une notification de **rétablissement** quand la valeur repasse au moins 3 points
  sous le seuil (hystérésis anti-oscillation). Une mesure absente ou illisible ne change pas l'état
  (« inconnu » n'est jamais « rétabli »).
- En cas de `429`, le cycle s'arrête pour respecter le quota du compte ; le suivant réessaie.
- Notifications en visibilité **privée** sur l'écran verrouillé ; permission `POST_NOTIFICATIONS`
  demandée à l'enregistrement sur Android 13+.
- Règles et états sont locaux (préférences privées), sans secret ; supprimés avec le profil.

## Limites

Aucune alerte si le téléphone est éteint, hors ligne, en économie d'énergie restrictive ou sans
notifications. Les mesures Ploi peuvent être différées. Une supervision garantie nécessite une
infrastructure externe.
