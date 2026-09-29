# Widgets de monitoring : mono et multi-serveurs

- Le widget **Serveur** affiche un seul serveur, ses mesures réelles et les jauges CPU/RAM/disque déjà présentes.
- Le widget **Serveurs** affiche jusqu'à **trois** serveurs dans une liste native défilante du lanceur. Chaque ligne comporte le nom, les mesures choisies, la charge si sélectionnée, les jauges CPU/RAM/disque lorsqu'une valeur en pourcentage valide existe, et un état périmé. L'espace inutilisé par l'ancien pavé de texte est remplacé par des lignes individuelles ; le nombre de lignes visibles dépend de la taille donnée par le lanceur. `load` n'est pas un pourcentage et n'est jamais inventé sous forme de jauge.
- Configuration : PIN avant d'énumérer les profils et serveurs ; profil explicite, choix radio unique en mono (un seul serveur, remplacé au clic) ou cases à cocher jusqu'à trois en multi : une fois trois serveurs choisis, les autres lignes sont désactivées jusqu'au retrait d'un serveur, résumé de la sélection avec possibilité de retirer un serveur, pages de 50 serveurs avec relance après erreur et sélection conservée entre pages. Le bouton d'enregistrement reste accessible en bas de l'écran. Les anciennes configurations de quatre à six serveurs sont ramenées aux trois premiers ; les anciennes configurations mono à leur premier serveur.
- Les lignes du widget proviennent exclusivement du cache local chiffré et de l'échantillon de monitoring réel. Le service de liste n'appelle pas Ploi et masque les données lorsqu'Android signale que l'appareil est verrouillé. Le worker conserve un rafraîchissement **au mieux**, soumis aux limites Android et Ploi : aucun intervalle exact de cinq minutes n'est garanti. Un nom n'est affiché que si une lecture réelle est présente ; à défaut, l'ID serveur réel est montré avec « Aucune mesure ».

**Vérification à effectuer sur appareil :** installation et redimensionnement des deux widgets sur le lanceur visé, défilement tactile de trois lignes, écran verrouillé/déverrouillé, activation biométrique/PIN et comportement de l'adaptateur de liste sur les versions Android prises en charge. Les tests JVM, lint et l'assemblage ne constituent pas une validation visuelle du lanceur.

## Présentation (refonte 0.3)

- Mises en page natives clair/sombre (`values/` et `values-night/widget_colors.xml`), contrastes AA vérifiés.
- Une barre de progression native par mesure en pourcentage (CPU, RAM, disque), avec la valeur écrite à côté ; couleur par palier (< 75 %, 75–89 %, ≥ 90 %) jamais utilisée seule. Une mesure absente ou illisible masque sa barre (jamais affichée à 0). La charge reste du texte.
- Statut Ploi du serveur affiché en mot (« ● Actif », « ● Problème »…), relu à chaque actualisation du widget.
- Configuration : étapes numérotées (profil, serveur(s), mesures), compteur de sélection, puces retirables, déverrouillage par le même pavé PIN 4 à 12 chiffres que l'application.
