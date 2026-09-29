# Accessibilité — informations non textuelles

## Portée de cet incrément

Ce document couvre uniquement ce que l'application **dessine** au lieu de l'écrire : les
indicateurs d'activité et les graphiques de monitoring. Ces éléments ne produisaient aucune
annonce, donc un lecteur d'écran ne percevait ni le chargement ni les mesures.

Livré (couvert par `AccessibilityContractTest`) :

- **`BusyIndicator`** (`Accessibility.kt`) : unique point de passage pour un indicateur d'activité
  indéterminé, porteur de `contentDescription` (`a11y_loading`). Les **69** appels directs à
  `CircularProgressIndicator` répartis dans **38** écrans ont été remplacés ; un test échoue si un
  indicateur muet réapparaît ailleurs que dans le composant d'accessibilité lui-même.
- **Graphiques de monitoring** (`MonitoringCharts.kt`) : chaque carte de pourcentage et la courbe de
  temps de réponse exposent un résumé parlé via `clearAndSetSemantics` (l'arbre interne est remplacé
  par une seule annonce, sans doublon de lecture) :
  - série avec tendance → dernière mesure, minimum, maximum et **nombre de relevés réels** ;
  - mesure unique → valeur annoncée explicitement comme « mesure unique, pas de tendance », jamais
    présentée comme une tendance ;
  - mesure illisible → « mesure illisible » ; série vide → « aucune mesure ». Une mesure inconnue
    n'est jamais remplacée par la précédente ni par zéro (`TrendSummary.latest` reste nul).
- **Seuil de disponibilité** : la barre d'uptime des moniteurs de site ne reposait que sur la
  couleur (primaire ≥ 95 %, erreur en dessous). Le seuil est désormais la constante partagée
  `UPTIME_HEALTHY_PERCENT` et l'état est **énoncé en mots** (`a11y_uptime_healthy` /
  `a11y_uptime_degraded`), la couleur n'étant plus le seul porteur d'information.
- **`formatPercent`** centralise le rendu des pourcentages (décimale supprimée seulement si elle est
  exactement nulle) et retourne `null` pour une mesure inconnue, afin qu'aucun appelant n'affiche un
  zéro de substitution.
- **FR/EN** : toutes les chaînes `a11y_*` existent dans `values` et `values-en` ; un test compare les
  clés réellement utilisées dans le code aux clés déclarées dans les deux locales.

Les `%1$d` de comptage sont placés en **fin** de chaîne pour éviter l'heuristique lint
`PluralsCandidate` sans la désactiver (ce sont des nombres de relevés, pas des pluriels à décliner).

## Explicitement NON couvert

- Les `RemoteViews` des widgets de lancement : ils n'utilisent pas ces composants et leurs jauges
  bitmap n'ont pas été traitées dans cet incrément.
- Aucune relecture des libellés d'action, de l'ordre de focus, des cibles tactiles, du contraste ni
  du comportement à grande taille de police.
- Aucun `Icon` décoratif n'existe aujourd'hui dans l'application (un seul icône de notification),
  donc rien à décrire de ce côté.

## Limites de validation

Tests JVM et contrats statiques sur les sources uniquement. **Aucune validation TalkBack** : ni
appareil ni émulateur n'était disponible. Les formulations annoncées n'ont donc pas été écoutées, et
l'accessibilité réelle de l'application n'est pas établie par cet incrément. Ne pas annoncer
« application accessible ».
