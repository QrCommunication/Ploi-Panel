# Design system et navigation (refonte UI 0.3.0)

Référence courte pour garder l'interface cohérente. Code : `PanelTheme.kt`, `DesignComponents.kt`,
`AppNavigation.kt`.

## Identité

- Console d'exploitation serveur : neutres ardoise froids (lisibilité des listes denses), une seule
  couleur de marque sarcelle profonde `#0B6E62` (reprise de l'icône) pour l'action principale et la
  destination sélectionnée. Pas de dégradé décoratif.
- Palettes clair/sombre complètes (y compris `surfaceContainer*`). Paires texte/fond vérifiées
  ≥ 4.5:1 (AA) : p. ex. primary/blanc 6.1, onSurfaceVariant/fond 8.5 (clair) et 10.8 (sombre),
  succès/avertissement/info sur leurs conteneurs ≥ 5.0.
- Couleurs sémantiques `PanelTheme.status` (success / warning / info + conteneurs), fournies par
  `PloiPanelTheme(dark)` via `LocalPanelStatusColors`. Aucun hex brut dans les écrans.
- Pas de couleur dynamique Android 12 : l'identité de marque reste stable et les contrastes vérifiés
  ne dépendent pas du fond d'écran.
- Typo : police système (aucun téléchargement), hiérarchie par taille/graisse ; `panelMonoStyle`
  pour IP, ports, versions, contenus de journaux.
- Espacements : grille 4 dp (`PanelSpacing`), cibles tactiles ≥ 48 dp, largeur de lecture max 720 dp
  pour formulaires/réglages sur tablette et pliable déplié.

## Composants

`StatusPill` (icône + mot + valeur Ploi brute, jamais la couleur seule ; statut inconnu jamais
présenté comme sain), `SectionCard`, `SectionHeader` (annoncé comme titre), `PanelListItem`
(≥ 56 dp, icône, titre, description, chevron), `MetricTile` (valeurs réelles uniquement),
`EmptyState`, `ErrorState` (message d'API + Réessayer), `LoadingState` (spinner annoncé + squelette
masqué à TalkBack), `IconBadge`, `InlineNotice`. `ApiErrorText` garde son API et gagne une icône.

## Icônes

`androidx.compose.material:material-icons-extended` (version de la BOM 2025.12.00 → 1.7.8), style
Outlined. R8 retire les icônes inutilisées en release. Icônes décoratives : `contentDescription =
null` ; boutons icône seuls : libellé traduit.

## Navigation

- `panelTab` reste la source unique (redirections widget, import de config, `TerminalNavigator`,
  verrou `globalBatchRunning`).
- 5 destinations : Serveurs (0), Terminal (10), Surveillance (9), Déploiement (8), Plus (11).
  `NavigationBar` sous 600 dp, `NavigationRail` à partir de 600 dp. Master/détail serveurs à ≥ 720 dp
  dans la zone de contenu.
- « Plus » (`MoreHubScreen`) liste 1..7 (Prestataires … Réglages) ; Retour y ramène.
- `PanelTopBar` : titre de section, retour contextuel, sélecteur de profil (même chemin que Réglages
  via `applyActiveProfile`, contrôle du jeton avant activation), « Gérer les profils » → Réglages,
  Déconnexion, action Verrouiller.
- Pendant la création de serveur la navigation et le sélecteur de profil sont désactivés : l'écran
  garde sa propre garde de sortie (instructions à usage unique).
- Détail serveur : héros + 5 catégories (Vue d'ensemble, Sites & apps, Données, Système, Accès)
  couvrant les 16 sous-écrans (`ServerSection`) ; chaque sous-écran s'ouvre dans un viewport pondéré
  avec retour borné à la liste des catégories.

## Correctifs 0.3.0

- **Fermeture sur Monitoring** : la langue intégrée remplace `LocalContext` par un contexte de configuration qui n'est pas l'Activity ; `rememberLauncherForActivityResult` (permission de notification des alertes et de la Surveillance) levait « No ActivityResultRegistryOwner ». `LocalizedActivityScope` fournit explicitement l'Activity comme propriétaire. Test Robolectric `LocalizedActivityScopeTest`, vérifié en échec sans le correctif.
- **PIN 4 à 12 chiffres** : politique unique `pinProblem()` ; la saisie accepte jusqu'à 12 chiffres, affiche un compteur et la règle exacte non respectée (suite, chiffre répété) pendant la frappe. Changement de PIN : même pavé et mêmes étapes que la création. Test Robolectric `PinEntryLengthTest` (8 et 12 chiffres, 13e ignoré).
- **Serveurs injoignables** : à l'ouverture de la liste (10 max par page) et du serveur, relecture `GET /servers/{id}` puis test TCP depuis le téléphone vers le port SSH indiqué par Ploi. Les deux résultats sont affichés séparément avec l'heure du test et un bouton Retester. Ploi ne documente aucun endpoint pour relancer sa propre sonde : l'app ne prétend pas la relancer.

## Limites

Aucune validation visuelle sur appareil ou émulateur n'a été faite pour cette refonte : les tests
sont des contrats de source et de logique pure, pas des rendus.
