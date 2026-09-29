# SSH direct — terminal, confiance et coffre de clés

## Portée de cet incrément

Livré (pur JVM, couvert par `SshKnownHostsTest` et `SshKeyVaultTest`) :

- **`SshHostTrustStore`** (`ssh/SshKnownHosts.kt`) : magasin de confiance des clés d'hôte,
  par profil Ploi, au format logique `known_hosts` (hôte, port, type de clé, blob). Règles
  strictes, sans aucun contournement silencieux :
  - premier contact → `UNKNOWN` : aucune connexion future ne pourra se faire sans une
    confirmation explicite de l'opérateur (empreinte `SHA256:` affichée, format OpenSSH) ;
  - clé identique → `TRUSTED` ;
  - clé différente → `MISMATCH` : **blocage dur** ; `trust()` refuse d'écraser et seul
    `repin()`, action délibérée, remplace une épingle ;
  - import de fichiers `known_hosts` existants avec rapport ligne par ligne ; les hôtes
    hachés (`|1|`), les jokers (`*`, `?`, `!`) et les lignes à marqueur (`@cert-authority`,
    `@revoked`) sont rejetés et signalés, jamais appliqués ; une ligne en conflit avec une
    épingle existante est signalée et ignorée, jamais substituée ;
  - magasin corrompu = échec fermé (`IllegalStateException`) plutôt qu'oubli silencieux des
    épingles ; `clear()` reste disponible comme réinitialisation explicite ;
  - `ssh-rsa` (signatures SHA-1) accepté pour les hôtes anciens mais marqué faible
    (`isWeakSignature`) afin que l'UI le signale.
- **`SshKeyVault`** (`ssh/SshKeyVault.kt`) : coffre de clés privées SSH importées, par profil.
  Le conteneur entier (métadonnées + PEM privés) est chiffré via le même `TokenCipher`
  Keystore que les jetons Ploi ; le PEM en clair n'atteint jamais le stockage. Formats
  acceptés : OpenSSH, PKCS#8 non chiffré, PEM RSA/EC historique non chiffré. Les blocs PEM
  protégés par phrase de passe sont **rejetés explicitement** (pas de support de déchiffrement
  pour l'instant). La clé publique déclarée doit décoder en base64 et correspondre
  structurellement au type annoncé (l'en-tête du blob SSH est vérifié).
- **Suppression de profil** : `ProfileStore.remove` efface désormais aussi le coffre SSH et
  le magasin de confiance du profil, sans toucher aux autres profils.
- **Écran de gestion** (`SshDeviceScreen.kt`, section « SSH sur cet appareil » des réglages,
  profil actif uniquement) :
  - liste des clés importées (nom, type, date d'ajout, alerte `ssh-rsa`) ; import par collage
    d'une ligne publique OpenSSH (`type base64 [commentaire]`, le commentaire est ignoré) et
    du PEM privé ; la suppression d'une clé exige une confirmation PIN/biométrie fraîche
    (`SensitiveConfirmDialog`) ; le PEM privé n'est **jamais** relu pour affichage ;
  - liste des hôtes épinglés (hôte:port, type, empreinte `SHA256:`, alerte `ssh-rsa`) ;
    retrait d'une épingle et effacement complet derrière des confirmations explicites ;
    import `known_hosts` par collage avec rapport visible (ajouts + lignes rejetées) tant que
    l'opérateur ne ferme pas la boîte de dialogue ;
  - le formulaire d'import reste ouvert en cas d'erreur de validation pour correction ;
  - alias Keystore dédié `ploi-panel.ssh-keys`, distinct des jetons Ploi et des modèles de
    scripts.
- **Vérification d'hôte à la connexion** (`SshProbeDialog.kt` + `ssh/SshHostKeyProbe.kt`) :
  - sonde SSH **poignée de main uniquement** (JSch, `SshHostKeyTransport`) : aucun identifiant,
    aucune authentification, aucun canal ni commande ; la clé d'hôte présentée est capturée via
    un dépôt qui répond toujours `NOT_INCLUDED` (vérification stricte, aucun `UserInfo`) — le
    transport n'accepte ni n'épingle jamais rien ;
  - décision pure et testée (`assessPresentedKey`, `SshHostKeyProbeTest`) : clé déjà épinglée et
    identique → confiance affichée ; premier contact → empreinte `SHA256:` affichée et épinglage
    **uniquement** sur confirmation explicite (`trust()`) ; clé différente → **blocage dur** avec
    empreintes épinglée et présentée côte à côte, ré-épinglage (`repin()`) derrière une
    confirmation PIN/biométrie fraîche ;
  - hôte/port validés avant tout trafic réseau, délai borné (10 s), sonde sur `Dispatchers.IO` ;
  - la boîte de dialogue de sonde est le **seul** point d'entrée UI autorisé à appeler
    `trust()`/`repin()` ; l'écran de gestion conserve l'interdiction (tests de contrat).

## Terminal SSH interactif (0.2.0)

- **Accès** : onglet « Terminal SSH » du panneau, ou bouton « Ouvrir un terminal SSH » d'un serveur
  (IP et `ssh_port` Ploi préremplis). Destinations mémorisées par profil (`SshBookmarkStore`) :
  hôte, port, utilisateur, référence de clé — **jamais** de mot de passe.
- **Connexion** (`ssh/SshShell.kt`, `JSchShellConnector`) : `StrictHostKeyChecking=yes` avec un dépôt
  de clés d'hôte en lecture seule adossé à `SshHostTrustStore` (`add()` ne fait rien,
  `promptYesNo` refuse). Hôte inconnu → `UnknownHost`, clé différente → `HostKeyMismatch` :
  la poignée de main s'arrête **avant** l'authentification, aucun mot de passe ni signature
  n'est émis. Les types de clé épinglés sont proposés en premier (comme OpenSSH) pour ne pas
  signaler à tort un premier contact. Transfert d'agent et X11 désactivés.
- **Authentification** : mot de passe (et `keyboard-interactive` limité à une invite « password »)
  ou clé du coffre. Les clés OpenSSH/PEM protégées par phrase de passe sont acceptées ; la phrase
  est demandée à chaque connexion et effacée ensuite. PKCS#8 chiffré reste refusé (non géré par
  JSch). Génération **Ed25519** sur l'appareil ; « Autoriser la clé sur ce serveur » envoie la
  seule clé publique via `POST /servers/{server}/ssh-keys` après confirmation PIN/biométrie.
- **Session** : PTY `xterm-256color`, redimensionnement suivant la surface (police réglable),
  keep-alive 30 s. `TerminalSession` relie flux SSH et émulateur sur des threads d'E/S dédiés ;
  les sessions survivent au verrouillage de l'application et au changement d'onglet, mais sont
  fermées sur déconnexion explicite, fin distante ou suppression du profil. Maximum 8 sessions.
- **Émulateur** (`ssh/TerminalEmulator.kt`, JVM pur) : UTF-8 (séquences coupées entre paquets),
  C0/ESC/CSI/OSC, SGR 16/256/24 bits, écran alternatif 1049, régions de défilement, insertion/
  suppression, tabulations, graphismes DEC, mode curseur applicatif, collage entre crochets
  (marqueur de fin injecté neutralisé), réponses DSR/DA. OSC 52 (presse-papiers), manipulation de
  fenêtre et souris sont **ignorés** : un serveur ne peut ni lire ni écrire le presse-papiers du
  téléphone. Paramètres bornés contre les séquences hostiles.
- **Saisie** : clavier logiciel (tampon invisible pour Retour arrière), clavier physique (flèches,
  F1–F12, Ctrl/Alt), barre de touches avec Ctrl/Alt collants, raccourcis Ctrl+C/D/Z/L, coller
  (entre crochets si demandé par l'hôte), copier l'écran et l'historique (marqué sensible).
- **Implémentations cryptographiques** : `JSchSupport` force les classes Bouncy Castle pour
  Ed25519/Ed448/X25519, absentes du JCA Android avant API 33.

## Explicitement NON implémenté

- Pas de SFTP/SCP, de redirection de ports, de transfert d'agent (refus volontaire), ni de
  reconnexion automatique ; pas de sélection de texte fine dans le terminal (copie de tout
  l'écran et de l'historique).
- La sonde capture une seule clé d'hôte par connexion (algorithme négocié) ; les autres
  algorithmes du serveur sont découverts au fil des vérifications, comme avec OpenSSH.

## Modèle de menace et règles permanentes

- La vérification de clé d'hôte ne doit jamais être désactivée silencieusement ; tout
  contournement est une action explicite et visible de l'opérateur.
- Clés privées : jamais en clair au repos, jamais dans les logs, jamais dans les sauvegardes
  Android non chiffrées ; exclues de l'[archive portable](portable-configuration.md) comme
  tout matériel lié au Keystore de l'appareil.
- Le socle étant par profil, supprimer un profil emporte ses clés et ses épingles.
- Les épingles d'hôtes sont des données publiques (clés publiques de serveurs) et ne sont
  pas chiffrées ; elles restent dans les préférences privées de l'application.

## Limites de validation

Tests JVM (`TerminalEmulatorTest`, `SshKeyMaterialTest`, `SshBookmarkStoreTest`) et tests de bout
en bout **contre un vrai serveur SSH embarqué** (Apache MINA SSHD, `SshShellEndToEndTest`) : refus
d'un hôte inconnu sans tentative d'authentification, blocage d'une clé modifiée, mauvais mot de
passe, session mot de passe avec PTY/écho/redimensionnement/fermeture, authentification par clé
Ed25519 générée, fin distante, hôte injoignable. Le Keystore Android réel, les claviers logiciels
tiers (IME), le rendu sur appareil/pliable et des serveurs Ploi réels n'ont **pas** été validés
sur appareil dans ce cycle.
