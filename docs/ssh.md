# SSH direct — cœur de confiance et coffre de clés

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

## Explicitement NON implémenté

- Aucune session authentifiée, aucun terminal, aucune exécution de commande : la sonde s'arrête
  à la poignée de main et aucune connexion authentifiée n'a été testée.
- Pas de génération de clés sur l'appareil, pas de passphrase PEM, pas d'agent forwarding.
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

Tests JVM uniquement (parsing, transitions de confiance, décision de la sonde, chiffrement
simulé par un chiffre de test) : le Keystore réel, une poignée de main SSH réelle et l'UI
associée n'ont pas été validés sur appareil ni contre un serveur. Ne pas annoncer « SSH
fonctionnel » à ce stade.
