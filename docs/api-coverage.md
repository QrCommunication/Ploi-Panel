# Inventaire de l’API HTTP Ploi

Extraction : 2026-09-24T18:31:36+00:00 ; source : [sitemap officiel](https://developers.ploi.io/sitemap.xml), puis en-têtes méthode/chemin des pages officielles. Base des requêtes : `https://ploi.io/api` (les chemins ci-dessous comprennent déjà `/api`).

**231 opérations documentées**, 230 pages avec routes, 231 pages de référence API ; **160 non implémentées** (`not_implemented`), **71 implémentées et testées localement mais non vérifiées sur compte réel** (`implemented_locally_unverified_live`). Ce tableau inventorie la documentation, et ne constitue ni une preuve de disponibilité dans un compte donné, ni un contrat testé contre l’API. Le [JSON](api-coverage.json) conserve chaque entrée et la classification de toutes les URL du sitemap.

## Périmètre et méthode

- 331 URL du sitemap : 231 pages de référence API, 12 guides généraux, 59 pages CLI, 28 pages Ploi Core (dont son **autre** « core-api »), 1 accueil. CLI et Ploi Core sont exclus du décompte Ploi HTTP API.
- Une opération = un en-tête de route HTTP (méthode + chemin `/api/...`) présent dans le corps d’une page de référence ; les exemples cURL/PHP dupliquant cet en-tête ne sont pas comptés. Les domaines sont les préfixes des URL documentaires, y compris `database` (backups) distinct de `databases`, et `site` (backups fichiers) distinct de `sites`.
- La page [types d’insights](https://developers.ploi.io/insights/insight-types) est une référence sans en-tête HTTP : incluse parmi les pages API, mais pas dans les opérations. [Création de serveur personnalisé](https://developers.ploi.io/servers/create-custom-server) décrit **deux** opérations, création puis démarrage de l’installation.
- Aucune route de création/modification d’un prestataire Ploi ou de création d’un moniteur n’a été extrapolée ; leur absence dans ce sitemap n’établit pas l’inexistence d’une API privée ou future. Schémas détaillés, scopes, limitations et effets asynchrones restent à lire sur chaque lien avant implémentation.

## Points d’attention pour l’application

- **Prestataires** : [liste](https://developers.ploi.io/user/list-server-providers) et [détail](https://developers.ploi.io/user/get-server-provider) retournent les prestataires liés au compte et leurs plans/régions. [Créer un serveur](https://developers.ploi.io/servers/create-server) requiert `plan`, `region`, `credential` ; l’ajout de prestataires passe par [l’interface du profil Ploi](https://ploi.io/profile/server-providers), pas par une route inventée. Un serveur personnalisé a son propre flux de création puis démarrage.
- **Monitoring serveur** : [serveurs suivis](https://developers.ploi.io/servers/monitored) et [mesures d’un serveur](https://developers.ploi.io/servers/monitoring) sont distincts. Les mesures d’exemple contiennent `cpu`, `ram`, `disk`, `load_average`, `date` et **pas de ping de site** ; l’installation du monitoring est nécessaire et une erreur documentée signale l’abonnement insuffisant. `install_monitoring` sur le démarrage d’un serveur personnalisé exige le plan Pro ou supérieur. Vérifier la fraîcheur des horodatages avant d’afficher « en direct ».
- **Moniteurs de site** : le domaine [monitoring](https://developers.ploi.io/monitoring/list-monitors) documente liste, détail, historique de réponses d’uptime et suppression pour un site. Ce n’est pas le même objet que les mesures serveur ; il n’y a pas de route de création documentée dans ce sitemap. Un contrôle HTTP(S) exécuté localement par le téléphone est une fonctionnalité différente, soumise aux limites de l’arrière-plan Android.
- **Transport** : [authentification](https://developers.ploi.io/getting-started/authorization) par jeton Bearer et [scopes](https://developers.ploi.io/getting-started/scopes) configurés par jeton ; [pagination](https://developers.ploi.io/getting-started/pagination) : `per_page` max 50 (au-delà, retour à 15) ; [limites](https://developers.ploi.io/getting-started/rate-limiting) : Basic 60, Pro 120, Unlimited 240 requêtes/minute, suivre les en-têtes de limite et l’attente de réessai.

## Opérations documentées — avancement vérifié localement

| Domaine | Méthode | Chemin documenté | Opération / source officielle | État |
|---|---|---|---|---|
| `user` | `GET` | `/api/user` | [Get user information](https://developers.ploi.io/user/information) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/server-providers` | [Get all server providers](https://developers.ploi.io/user/list-server-providers) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/server-providers/{provider}` | [Get server provider](https://developers.ploi.io/user/get-server-provider) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/backup-configurations` | [Get all backup configurations](https://developers.ploi.io/user/list-backup-configurations) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/backup-configurations/{backupConfiguration}` | [Get backup configuration](https://developers.ploi.io/user/get-backup-configuration) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/notification-channels` | [Get all notification channels](https://developers.ploi.io/user/list-notification-channels) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/source-control` | [Get all source control providers](https://developers.ploi.io/user/list-source-control-providers) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/source-control/{provider}` | [Get source control provider](https://developers.ploi.io/user/show-source-control-providers) | `implemented_locally_unverified_live` |
| `user` | `GET` | `/api/user/source-control/{provider}/repositories` | [Get repositories](https://developers.ploi.io/user/repositories-for-source-control-providers) | `implemented_locally_unverified_live` |
| `scripts` | `GET` | `/api/scripts` | [List all scripts](https://developers.ploi.io/scripts/list-scripts) | `not_implemented` |
| `scripts` | `POST` | `/api/scripts` | [Create a script](https://developers.ploi.io/scripts/create-script) | `not_implemented` |
| `scripts` | `GET` | `/api/scripts/{script}` | [Get a script](https://developers.ploi.io/scripts/get-script) | `not_implemented` |
| `scripts` | `PATCH` | `/api/scripts/{script}` | [Update a script](https://developers.ploi.io/scripts/update-script) | `not_implemented` |
| `scripts` | `DELETE` | `/api/scripts/{script}` | [Delete a script](https://developers.ploi.io/scripts/delete-script) | `not_implemented` |
| `scripts` | `POST` | `/api/scripts/{script}/run` | [Run a script](https://developers.ploi.io/scripts/run-script) | `not_implemented` |
| `scripts` | `POST` | `/api/servers/{server}/scripts/run` | [Run a one-off script](https://developers.ploi.io/scripts/run-one-off-script) | `not_implemented` |
| `scripts` | `GET` | `/api/servers/{server}/scripts/run/{execution}` | [Get a script execution](https://developers.ploi.io/scripts/get-script-execution) | `not_implemented` |
| `script-schedules` | `GET` | `/api/scripts/{script}/schedules` | [List all schedules](https://developers.ploi.io/script-schedules/list-schedules) | `not_implemented` |
| `script-schedules` | `POST` | `/api/scripts/{script}/schedules` | [Create a schedule](https://developers.ploi.io/script-schedules/create-schedule) | `not_implemented` |
| `script-schedules` | `GET` | `/api/scripts/{script}/schedules/{schedule}` | [Get a schedule](https://developers.ploi.io/script-schedules/get-schedule) | `not_implemented` |
| `script-schedules` | `PATCH` | `/api/scripts/{script}/schedules/{schedule}` | [Update a schedule](https://developers.ploi.io/script-schedules/update-schedule) | `not_implemented` |
| `script-schedules` | `POST` | `/api/scripts/{script}/schedules/{schedule}/toggle` | [Toggle a schedule](https://developers.ploi.io/script-schedules/toggle-schedule) | `not_implemented` |
| `script-schedules` | `DELETE` | `/api/scripts/{script}/schedules/{schedule}` | [Delete a schedule](https://developers.ploi.io/script-schedules/delete-schedule) | `not_implemented` |
| `script-actions` | `GET` | `/api/scripts/{script}/actions` | [List all actions](https://developers.ploi.io/script-actions/list-actions) | `not_implemented` |
| `script-actions` | `POST` | `/api/scripts/{script}/actions` | [Create an action](https://developers.ploi.io/script-actions/create-action) | `not_implemented` |
| `script-actions` | `GET` | `/api/scripts/{script}/actions/{action}` | [Get an action](https://developers.ploi.io/script-actions/get-action) | `not_implemented` |
| `script-actions` | `PATCH` | `/api/scripts/{script}/actions/{action}` | [Update an action](https://developers.ploi.io/script-actions/update-action) | `not_implemented` |
| `script-actions` | `POST` | `/api/scripts/{script}/actions/{action}/toggle` | [Toggle an action](https://developers.ploi.io/script-actions/toggle-action) | `not_implemented` |
| `script-actions` | `POST` | `/api/scripts/{script}/actions/{action}/rotate-secret` | [Rotate the secret](https://developers.ploi.io/script-actions/rotate-action-secret) | `not_implemented` |
| `script-actions` | `DELETE` | `/api/scripts/{script}/actions/{action}` | [Delete an action](https://developers.ploi.io/script-actions/delete-action) | `not_implemented` |
| `status-pages` | `GET` | `/api/status-pages` | [List status pages](https://developers.ploi.io/status-pages/list-status-pages) | `not_implemented` |
| `status-pages` | `GET` | `/api/status-pages/{statusPage}` | [Get status page](https://developers.ploi.io/status-pages/get-status-page) | `not_implemented` |
| `status-pages` | `GET` | `/api/status-pages/{statusPage}/incidents` | [Get status page incidents](https://developers.ploi.io/status-pages/get-status-page-incidents) | `not_implemented` |
| `status-pages` | `POST` | `/api/status-pages/{statusPage}/incidents` | [Create status page incident](https://developers.ploi.io/status-pages/create-status-page-incident) | `not_implemented` |
| `status-pages` | `DELETE` | `/api/status-pages/{statusPage}/incident/{incident}` | [Delete status page incident](https://developers.ploi.io/status-pages/delete-status-page-incident) | `not_implemented` |
| `webserver-templates` | `GET` | `/api/webserver-templates` | [List webserver templates](https://developers.ploi.io/webserver-templates/list-webserver-templates) | `not_implemented` |
| `webserver-templates` | `GET` | `/api/webserver-templates/{id}` | [Get webserver template](https://developers.ploi.io/webserver-templates/get-webserver-template) | `not_implemented` |
| `projects` | `GET` | `/api/projects` | [List all projects](https://developers.ploi.io/projects/list-projects) | `not_implemented` |
| `projects` | `POST` | `/api/projects` | [Create a project](https://developers.ploi.io/projects/create-project) | `not_implemented` |
| `projects` | `GET` | `/api/projects/{project}` | [Get a project](https://developers.ploi.io/projects/get-project) | `not_implemented` |
| `projects` | `PATCH` | `/api/projects/{project}` | [Update a project](https://developers.ploi.io/projects/update-project) | `not_implemented` |
| `projects` | `DELETE` | `/api/projects/{project}` | [Delete a project](https://developers.ploi.io/projects/delete-project) | `not_implemented` |
| `servers` | `GET` | `/api/servers` | [List all servers](https://developers.ploi.io/servers/list-servers) | `implemented_locally_unverified_live` |
| `servers` | `GET` | `/api/servers/{id}` | [Get a server](https://developers.ploi.io/servers/get-server) | `implemented_locally_unverified_live` |
| `servers` | `POST` | `/api/servers` | [Create a server](https://developers.ploi.io/servers/create-server) | `implemented_locally_unverified_live` |
| `servers` | `POST` | `/api/servers/custom` | [Create a custom server](https://developers.ploi.io/servers/create-custom-server) | `implemented_locally_unverified_live` |
| `servers` | `POST` | `/api/servers/custom/{id}/start` | [Start installation](https://developers.ploi.io/servers/create-custom-server) | `implemented_locally_unverified_live` |
| `servers` | `PATCH` | `/api/servers/{server}` | [Update a server](https://developers.ploi.io/servers/update-server) | `implemented_locally_unverified_live` |
| `servers` | `DELETE` | `/api/servers/{server}` | [Delete a server](https://developers.ploi.io/servers/delete-server) | `implemented_locally_unverified_live` |
| `servers` | `GET` | `/api/servers/{id}/logs` | [Get server logs](https://developers.ploi.io/servers/logs-server) | `implemented_locally_unverified_live` |
| `servers` | `GET` | `/api/servers/monitored` | [List monitored servers](https://developers.ploi.io/servers/monitored) | `implemented_locally_unverified_live` |
| `servers` | `GET` | `/api/servers/{server}/monitor` | [Get monitoring data](https://developers.ploi.io/servers/monitoring) | `implemented_locally_unverified_live` |
| `servers` | `POST` | `/api/servers/{id}/restart` | [Restart a server](https://developers.ploi.io/servers/restart-server) | `implemented_locally_unverified_live` |
| `databases` | `GET` | `/api/servers/{server}/databases` | [List all databases](https://developers.ploi.io/databases/list-databases) | `implemented_locally_unverified_live` |
| `databases` | `POST` | `/api/servers/{server}/databases` | [Create a database](https://developers.ploi.io/databases/create-database) | `implemented_locally_unverified_live` |
| `databases` | `GET` | `/api/servers/{server}/databases/{id}` | [Get a database](https://developers.ploi.io/databases/get-database) | `implemented_locally_unverified_live` |
| `databases` | `DELETE` | `/api/servers/{server}/databases/{id}` | [Delete a database](https://developers.ploi.io/databases/delete-database) | `implemented_locally_unverified_live` |
| `databases` | `POST` | `/api/servers/{server}/databases/acknowledge` | [Acknowledge a database](https://developers.ploi.io/databases/acknowledge-database) | `implemented_locally_unverified_live` |
| `databases` | `DELETE` | `/api/servers/{server}/databases/{id}/forget` | [Forget a database](https://developers.ploi.io/databases/forget-database) | `implemented_locally_unverified_live` |
| `databases` | `POST` | `/api/servers/{server}/databases/{database}/duplicate` | [Duplicate a database](https://developers.ploi.io/databases/duplicate-database) | `implemented_locally_unverified_live` |
| `database-users` | `GET` | `/api/servers/{server}/databases/{database}/users` | [List all database users](https://developers.ploi.io/database-users/list-database-users) | `implemented_locally_unverified_live` |
| `database-users` | `POST` | `/api/servers/{server}/databases/{database}/users` | [Create a database user](https://developers.ploi.io/database-users/create-database-user) | `implemented_locally_unverified_live` |
| `database-users` | `GET` | `/api/servers/{server}/databases/{database}/users/{user}` | [Get a database user](https://developers.ploi.io/database-users/get-database-user) | `implemented_locally_unverified_live` |
| `database-users` | `DELETE` | `/api/servers/{server}/databases/{database}/users/{user}` | [Delete a database user](https://developers.ploi.io/database-users/delete-database-user) | `implemented_locally_unverified_live` |
| `database-users` | `POST` | `/api/servers/{server}/databases/{database}/users/attach` | [Attach user to database](https://developers.ploi.io/database-users/attach-user-to-database) | `implemented_locally_unverified_live` |
| `crontabs` | `GET` | `/api/servers/{server}/crontabs` | [List all crontabs](https://developers.ploi.io/crontabs/list-crontabs) | `not_implemented` |
| `crontabs` | `POST` | `/api/servers/{server}/crontabs` | [Create a crontab](https://developers.ploi.io/crontabs/create-crontab) | `not_implemented` |
| `crontabs` | `GET` | `/api/servers/{server}/crontabs/{id}` | [Get a crontab](https://developers.ploi.io/crontabs/get-crontab) | `not_implemented` |
| `crontabs` | `DELETE` | `/api/servers/{server}/crontabs/{id}` | [Delete a crontab](https://developers.ploi.io/crontabs/delete-crontab) | `not_implemented` |
| `network-rules` | `GET` | `/api/servers/{server}/network-rules` | [List all network rules](https://developers.ploi.io/network-rules/list-network-rules) | `not_implemented` |
| `network-rules` | `POST` | `/api/servers/{server}/network-rules` | [Create a network rule](https://developers.ploi.io/network-rules/create-network-rule) | `not_implemented` |
| `network-rules` | `GET` | `/api/servers/{server}/network-rules/{id}` | [Get a network rule](https://developers.ploi.io/network-rules/get-network-rule) | `not_implemented` |
| `network-rules` | `DELETE` | `/api/servers/{server}/network-rules/{id}` | [Delete a network rule](https://developers.ploi.io/network-rules/delete-network-rule) | `not_implemented` |
| `daemons` | `GET` | `/api/servers/{server}/daemons` | [List all daemons](https://developers.ploi.io/daemons/list-daemons) | `not_implemented` |
| `daemons` | `POST` | `/api/servers/{server}/daemons` | [Create a daemon](https://developers.ploi.io/daemons/create-daemon) | `not_implemented` |
| `daemons` | `GET` | `/api/servers/{server}/daemons/{daemon}` | [Get a daemon](https://developers.ploi.io/daemons/get-daemon) | `not_implemented` |
| `daemons` | `POST` | `/api/servers/{server}/daemons/{daemon}/restart` | [Restart a daemon](https://developers.ploi.io/daemons/restart-daemon) | `not_implemented` |
| `daemons` | `POST` | `/api/servers/{server}/daemons/{daemon}/toggle-pause` | [Toggle pause daemon](https://developers.ploi.io/daemons/pause-daemon) | `not_implemented` |
| `daemons` | `DELETE` | `/api/servers/{server}/daemons/{daemon}` | [Delete a daemon](https://developers.ploi.io/daemons/delete-daemon) | `not_implemented` |
| `system-users` | `GET` | `/api/servers/{server}/system-users` | [List all system users](https://developers.ploi.io/system-users/list-system-users) | `not_implemented` |
| `system-users` | `POST` | `/api/servers/{server}/system-users` | [Create a system user](https://developers.ploi.io/system-users/create-system-user) | `not_implemented` |
| `system-users` | `GET` | `/api/servers/{server}/system-users/{systemUser}` | [Get a system user](https://developers.ploi.io/system-users/get-system-user) | `not_implemented` |
| `system-users` | `DELETE` | `/api/servers/{server}/system-users/{systemUser}` | [Delete a system user](https://developers.ploi.io/system-users/delete-system-user) | `not_implemented` |
| `services` | `POST` | `/api/servers/{server}/services/{service}/restart` | [Restart a service](https://developers.ploi.io/services/restart-service) | `not_implemented` |
| `services` | `POST` | `/api/servers/{server}/services/{service}/reload` | [Reload a service](https://developers.ploi.io/services/reload-service) | `not_implemented` |
| `services` | `POST` | `/api/servers/{server}/install/wp-cli` | [Install WordPress CLI](https://developers.ploi.io/services/install-wordpress-cli) | `not_implemented` |
| `services` | `POST` | `/api/servers/{server}/wp-cli/run` | [Run a WP CLI command](https://developers.ploi.io/services/run-wp-cli-command) | `not_implemented` |
| `services` | `DELETE` | `/api/servers/{server}/uninstall/wp-cli` | [Uninstall WordPress CLI](https://developers.ploi.io/services/uninstall-wordpress-cli) | `not_implemented` |
| `php` | `POST` | `/api/servers/{server}/refresh-opcache` | [Refresh OPcache](https://developers.ploi.io/php/refresh-opcache) | `not_implemented` |
| `php` | `POST` | `/api/servers/{server}/enable-opcache` | [Enable OPcache](https://developers.ploi.io/php/enable-opcache) | `not_implemented` |
| `php` | `DELETE` | `/api/servers/{server}/disable-opcache` | [Disable OPcache](https://developers.ploi.io/php/disable-opcache) | `not_implemented` |
| `php` | `GET` | `/api/servers/{server}/php/versions` | [Get installed PHP versions](https://developers.ploi.io/php/installed-php-versions) | `not_implemented` |
| `php` | `POST` | `/api/servers/{server}/php/install` | [Install PHP version](https://developers.ploi.io/php/install-php-version) | `not_implemented` |
| `php` | `POST` | `/api/servers/{server}/php/cli-version` | [Switch PHP CLI version](https://developers.ploi.io/php/switch-php-cli-version) | `not_implemented` |
| `ssh-keys` | `GET` | `/api/servers/{server}/ssh-keys` | [List all SSH keys](https://developers.ploi.io/ssh-keys/list-ssh-keys) | `not_implemented` |
| `ssh-keys` | `POST` | `/api/servers/{server}/ssh-keys` | [Create a new SSH key](https://developers.ploi.io/ssh-keys/create-ssh-key) | `not_implemented` |
| `ssh-keys` | `GET` | `/api/servers/{server}/ssh-keys/{sshKey}` | [Get an SSH key](https://developers.ploi.io/ssh-keys/get-ssh-key) | `not_implemented` |
| `ssh-keys` | `DELETE` | `/api/servers/{server}/ssh-keys/{sshKey}` | [Delete an SSH key](https://developers.ploi.io/ssh-keys/delete-ssh-key) | `not_implemented` |
| `load-balancers` | `PATCH` | `/api/servers/{server}/load-balancer/attach` | [Attach server](https://developers.ploi.io/load-balancers/attach-server) | `not_implemented` |
| `load-balancers` | `PATCH` | `/api/servers/{server}/load-balancer/detach` | [Detach server](https://developers.ploi.io/load-balancers/detach-server) | `not_implemented` |
| `load-balancers` | `POST` | `/api/servers/{server}/load-balancer/{domain}/request-certificate` | [Request certificate](https://developers.ploi.io/load-balancers/request-certificate-for-domain) | `not_implemented` |
| `load-balancers` | `DELETE` | `/api/servers/{server}/load-balancer/{domain}/revoke-certificate` | [Revoke certificate](https://developers.ploi.io/load-balancers/revoke-certificate-for-domain) | `not_implemented` |
| `insights` | `GET` | `/api/servers/{server}/insights` | [List all insights](https://developers.ploi.io/insights/list-insights) | `not_implemented` |
| `insights` | `GET` | `/api/servers/{server}/insights/{id}` | [Get an insight](https://developers.ploi.io/insights/get-insight) | `not_implemented` |
| `insights` | `GET` | `/api/servers/{server}/insights/{id}/detail` | [Get insight details](https://developers.ploi.io/insights/detail-insight) | `not_implemented` |
| `insights` | `POST` | `/api/servers/{server}/insights/{id}/automatically-fix` | [Automatically fix insight](https://developers.ploi.io/insights/automatically-fix-insight) | `not_implemented` |
| `insights` | `POST` | `/api/servers/{server}/insights/{id}/ignore` | [Ignore insight](https://developers.ploi.io/insights/ignore-insight) | `not_implemented` |
| `insights` | `DELETE` | `/api/servers/{server}/insights/{id}` | [Delete insight](https://developers.ploi.io/insights/delete-insight) | `not_implemented` |
| `sites` | `GET` | `/api/servers/{server}/sites` | [Get all sites](https://developers.ploi.io/sites/list-sites) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites` | [Create a site](https://developers.ploi.io/sites/create-site) | `implemented_locally_unverified_live` |
| `sites` | `GET` | `/api/servers/{server}/sites/{id}` | [Get site](https://developers.ploi.io/sites/get-site) | `implemented_locally_unverified_live` |
| `sites` | `PATCH` | `/api/servers/{server}/sites/{site}` | [Update site](https://developers.ploi.io/sites/update-site) | `implemented_locally_unverified_live` |
| `sites` | `DELETE` | `/api/servers/{server}/sites/{id}` | [Delete site](https://developers.ploi.io/sites/delete-site) | `implemented_locally_unverified_live` |
| `sites` | `GET` | `/api/servers/{server}/sites/{id}/log` | [Get site logs](https://developers.ploi.io/sites/log-site) | `implemented_locally_unverified_live` |
| `sites` | `GET` | `/api/servers/{server}/sites/{id}/log/{log}` | [Get log entry](https://developers.ploi.io/sites/get-log-site) | `implemented_locally_unverified_live` |
| `sites` | `GET` | `/api/servers/{server}/sites/{id}/test-domain` | [Get test domain](https://developers.ploi.io/sites/get-test-domain) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites/{id}/test-domain` | [Enable test domain](https://developers.ploi.io/sites/enable-test-domain) | `implemented_locally_unverified_live` |
| `sites` | `DELETE` | `/api/servers/{server}/sites/{id}/test-domain` | [Disable test domain](https://developers.ploi.io/sites/disable-test-domain) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites/{id}/suspend` | [Suspend site](https://developers.ploi.io/sites/suspend-site) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites/{id}/resume` | [Resume site](https://developers.ploi.io/sites/resume-site) | `implemented_locally_unverified_live` |
| `sites` | `PATCH` | `/api/servers/{server}/sites/{id}` | [Update robot access](https://developers.ploi.io/sites/robot-access) | `implemented_locally_unverified_live` |
| `sites` | `GET` | `/api/servers/{server}/sites/laravel/horizon/{type}` | [Get Horizon statistics](https://developers.ploi.io/sites/laravel-horizon-statistics) | `implemented_locally_unverified_live` |
| `sites` | `GET` | `/api/servers/{server}/sites/{site}/nginx-configuration` | [Get NGINX configuration](https://developers.ploi.io/sites/get-nginx-configuration) | `implemented_locally_unverified_live` |
| `sites` | `PATCH` | `/api/servers/{server}/sites/{site}/nginx-configuration` | [Update NGINX configuration](https://developers.ploi.io/sites/update-nginx-configuration) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites/{site}/clone` | [Clone site](https://developers.ploi.io/sites/clone-site) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites/{site}/php-version` | [Change PHP version](https://developers.ploi.io/sites/change-php-version) | `implemented_locally_unverified_live` |
| `sites` | `POST` | `/api/servers/{server}/sites/{id}/permission-reset` | [Reset permissions](https://developers.ploi.io/sites/reset-site-permissions) | `implemented_locally_unverified_live` |
| `deployments` | `GET` | `/api/servers/{server}/sites/{id}/deploy/script` | [Get deploy script](https://developers.ploi.io/deployments/get-deploy-script) | `implemented_locally_unverified_live` |
| `deployments` | `PATCH` | `/api/servers/{server}/sites/{id}/deploy/script` | [Update deploy script](https://developers.ploi.io/deployments/update-deploy-script) | `implemented_locally_unverified_live` |
| `deployments` | `POST` | `/api/servers/{server}/sites/{id}/deploy` | [Deploy site](https://developers.ploi.io/deployments/deploy-site) | `implemented_locally_unverified_live` |
| `deployments` | `POST` | `/api/servers/{server}/sites/{id}/deploy-to-production` | [Deploy to production](https://developers.ploi.io/deployments/deploy-staging-site-to-production) | `implemented_locally_unverified_live` |
| `repositories` | `GET` | `/api/servers/{server}/sites/{site}/repository` | [Get repository](https://developers.ploi.io/repositories/get-repository) | `implemented_locally_unverified_live` |
| `repositories` | `POST` | `/api/servers/{server}/sites/{site}/repository` | [Install repository](https://developers.ploi.io/repositories/install-repository) | `implemented_locally_unverified_live` |
| `repositories` | `POST` | `/api/servers/{server}/sites/{site}/repository/custom-deployments` | [Enable custom deployments](https://developers.ploi.io/repositories/custom-deployment) | `implemented_locally_unverified_live` |
| `repositories` | `DELETE` | `/api/servers/{server}/sites/{site}/repository` | [Delete repository](https://developers.ploi.io/repositories/delete-repository) | `implemented_locally_unverified_live` |
| `repositories` | `POST` | `/api/servers/{server}/sites/{site}/repository/quick-deploy` | [Toggle quick deploy](https://developers.ploi.io/repositories/toggle-quick-deploy) | `implemented_locally_unverified_live` |
| `environment` | `GET` | `/api/servers/{server}/sites/{id}/env` | [Get environment file](https://developers.ploi.io/environment/get-env-from-site) | `implemented_locally_unverified_live` |
| `environment` | `PATCH` | `/api/servers/{server}/sites/{id}/env` | [Update environment file](https://developers.ploi.io/environment/update-env-from-site) | `implemented_locally_unverified_live` |
| `queue-workers` | `GET` | `/api/servers/{server}/sites/{id}/queues` | [Get all queue workers](https://developers.ploi.io/queue-workers/list-queue-workers) | `not_implemented` |
| `queue-workers` | `POST` | `/api/servers/{server}/sites/{id}/queues` | [Create queue worker](https://developers.ploi.io/queue-workers/create-queue-worker) | `not_implemented` |
| `queue-workers` | `GET` | `/api/servers/{server}/sites/{id}/queues/{queueId}` | [Get queue worker](https://developers.ploi.io/queue-workers/get-queue-worker) | `not_implemented` |
| `queue-workers` | `POST` | `/api/servers/{server}/sites/{id}/queues/{queueId}/restart` | [Restart queue worker](https://developers.ploi.io/queue-workers/restart-queue-worker) | `not_implemented` |
| `queue-workers` | `POST` | `/api/servers/{server}/sites/{id}/queues/{queueId}/toggle-pause` | [Toggle pause](https://developers.ploi.io/queue-workers/pause-queue-worker) | `not_implemented` |
| `queue-workers` | `DELETE` | `/api/servers/{server}/sites/{id}/queues/{queueId}` | [Delete queue worker](https://developers.ploi.io/queue-workers/delete-queue-worker) | `not_implemented` |
| `redirects` | `GET` | `/api/servers/{server}/sites/{site}/redirects` | [Get all redirects](https://developers.ploi.io/redirects/list-redirects) | `not_implemented` |
| `redirects` | `POST` | `/api/servers/{server}/sites/{site}/redirects` | [Create redirect](https://developers.ploi.io/redirects/create-redirect) | `not_implemented` |
| `redirects` | `GET` | `/api/servers/{server}/sites/{site}/redirects/{redirect}` | [Get redirect](https://developers.ploi.io/redirects/get-redirect) | `not_implemented` |
| `redirects` | `DELETE` | `/api/servers/{server}/sites/{site}/redirects/{redirect}` | [Delete redirect](https://developers.ploi.io/redirects/delete-redirect) | `not_implemented` |
| `certificates` | `GET` | `/api/servers/{server}/sites/{site}/certificates` | [Get all certificates](https://developers.ploi.io/certificates/list-certificates) | `not_implemented` |
| `certificates` | `POST` | `/api/servers/{server}/sites/{site}/certificates` | [Create certificate](https://developers.ploi.io/certificates/create-certificate) | `not_implemented` |
| `certificates` | `GET` | `/api/servers/{server}/sites/{site}/certificates/{certificate}` | [Get certificate](https://developers.ploi.io/certificates/get-certificate) | `not_implemented` |
| `certificates` | `GET` | `/api/servers/{server}/sites/{site}/certificates/{certificate}/download` | [Download certificate](https://developers.ploi.io/certificates/download-certificate) | `not_implemented` |
| `certificates` | `POST` | `/api/servers/{server}/sites/{site}/certificates/{certificate}/activate` | [Activate certificate](https://developers.ploi.io/certificates/activate-certificate) | `not_implemented` |
| `certificates` | `DELETE` | `/api/servers/{server}/sites/{site}/certificates/{certificate}` | [Delete certificate](https://developers.ploi.io/certificates/delete-certificate) | `not_implemented` |
| `auth-users` | `GET` | `/api/servers/{server}/sites/{id}/auth-users` | [List all auth users](https://developers.ploi.io/auth-users/list-auth-users) | `not_implemented` |
| `auth-users` | `POST` | `/api/servers/{server}/sites/{id}/auth-users` | [Create an auth user](https://developers.ploi.io/auth-users/create-auth-user) | `not_implemented` |
| `auth-users` | `GET` | `/api/servers/{server}/sites/{id}/auth-users/{authUserId}` | [Get an auth user](https://developers.ploi.io/auth-users/get-auth-user) | `not_implemented` |
| `auth-users` | `DELETE` | `/api/servers/{server}/sites/{id}/auth-users/{authUserId}` | [Delete an auth user](https://developers.ploi.io/auth-users/delete-auth-user) | `not_implemented` |
| `aliases` | `GET` | `/api/servers/{server}/sites/{site}/aliases` | [List all aliases](https://developers.ploi.io/aliases/list-aliases) | `not_implemented` |
| `aliases` | `POST` | `/api/servers/{server}/sites/{site}/aliases` | [Create aliases](https://developers.ploi.io/aliases/create-alias) | `not_implemented` |
| `aliases` | `DELETE` | `/api/servers/{server}/sites/{site}/aliases/{alias}` | [Delete an alias](https://developers.ploi.io/aliases/delete-alias) | `not_implemented` |
| `tenants` | `GET` | `/api/servers/{server}/sites/{site}/tenants` | [List all tenants](https://developers.ploi.io/tenants/list-tenants) | `not_implemented` |
| `tenants` | `POST` | `/api/servers/{server}/sites/{site}/tenants` | [Create a tenant](https://developers.ploi.io/tenants/create-tenant) | `not_implemented` |
| `tenants` | `DELETE` | `/api/servers/{server}/sites/{site}/tenants/{tenant}` | [Delete a tenant](https://developers.ploi.io/tenants/delete-tenant) | `not_implemented` |
| `tenants` | `POST` | `/api/servers/{server}/sites/{site}/tenants/{tenant}/request-certificate` | [Request a certificate](https://developers.ploi.io/tenants/request-certificate) | `not_implemented` |
| `tenants` | `POST` | `/api/servers/{server}/sites/{site}/tenants/{tenant}/revoke-certificate` | [Revoke a certificate](https://developers.ploi.io/tenants/delete-certificate) | `not_implemented` |
| `tenants` | `GET` | `/api/servers/{server}/sites/{site}/tenants/{tenant}/nginx-configuration` | [Get NGINX configuration](https://developers.ploi.io/tenants/get-nginx-configuration-tenant) | `not_implemented` |
| `tenants` | `PATCH` | `/api/servers/{server}/sites/{site}/tenants/{tenant}/nginx-configuration` | [Update NGINX configuration](https://developers.ploi.io/tenants/update-nginx-configuration-tenant) | `not_implemented` |
| `monitoring` | `GET` | `/api/servers/{server}/sites/{site}/monitors` | [List all monitors](https://developers.ploi.io/monitoring/list-monitors) | `not_implemented` |
| `monitoring` | `GET` | `/api/servers/{server}/sites/{site}/monitors/{monitor}` | [Get a monitor](https://developers.ploi.io/monitoring/get-monitor) | `not_implemented` |
| `monitoring` | `GET` | `/api/servers/{server}/sites/{site}/monitors/{monitor}/uptime-responses` | [Get uptime responses](https://developers.ploi.io/monitoring/get-uptime-responses) | `not_implemented` |
| `monitoring` | `DELETE` | `/api/servers/{server}/sites/{site}/monitors/{monitor}` | [Delete a monitor](https://developers.ploi.io/monitoring/delete-monitor) | `not_implemented` |
| `apps` | `POST` | `/api/servers/{server}/sites/{id}/wordpress` | [Install WordPress](https://developers.ploi.io/apps/install-wordpress) | `not_implemented` |
| `apps` | `DELETE` | `/api/servers/{server}/sites/{id}/wordpress` | [Uninstall WordPress](https://developers.ploi.io/apps/uninstall-wordpress) | `not_implemented` |
| `apps` | `POST` | `/api/servers/{server}/sites/{id}/nextcloud` | [Install Nextcloud](https://developers.ploi.io/apps/install-nextcloud) | `not_implemented` |
| `apps` | `DELETE` | `/api/servers/{server}/sites/{id}/nextcloud` | [Uninstall Nextcloud](https://developers.ploi.io/apps/uninstall-nextcloud) | `not_implemented` |
| `apps` | `POST` | `/api/servers/{server}/sites/{id}/statamic` | [Install Statamic](https://developers.ploi.io/apps/install-statamic) | `not_implemented` |
| `apps` | `DELETE` | `/api/servers/{server}/sites/{id}/statamic` | [Uninstall Statamic](https://developers.ploi.io/apps/uninstall-statamic) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/complete-install` | [Complete install](https://developers.ploi.io/wordpress-management/complete-install) | `not_implemented` |
| `wordpress-management` | `GET` | `/api/servers/{server}/sites/{id}/wordpress/plugins` | [List plugins](https://developers.ploi.io/wordpress-management/list-plugins) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/plugins/activate` | [Activate plugin](https://developers.ploi.io/wordpress-management/activate-plugin) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/plugins/deactivate` | [Deactivate plugin](https://developers.ploi.io/wordpress-management/deactivate-plugin) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/plugins/update` | [Update plugin](https://developers.ploi.io/wordpress-management/update-plugin) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/plugins/install` | [Install plugin](https://developers.ploi.io/wordpress-management/install-plugin) | `not_implemented` |
| `wordpress-management` | `DELETE` | `/api/servers/{server}/sites/{id}/wordpress/plugins/delete` | [Delete plugin](https://developers.ploi.io/wordpress-management/delete-plugin) | `not_implemented` |
| `wordpress-management` | `GET` | `/api/servers/{server}/sites/{id}/wordpress/themes` | [List themes](https://developers.ploi.io/wordpress-management/list-themes) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/themes/activate` | [Activate theme](https://developers.ploi.io/wordpress-management/activate-theme) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/themes/update` | [Update theme](https://developers.ploi.io/wordpress-management/update-theme) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/themes/install` | [Install theme](https://developers.ploi.io/wordpress-management/install-theme) | `not_implemented` |
| `wordpress-management` | `DELETE` | `/api/servers/{server}/sites/{id}/wordpress/themes/delete` | [Delete theme](https://developers.ploi.io/wordpress-management/delete-theme) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/toggle-xmlrpc` | [Toggle XML-RPC](https://developers.ploi.io/wordpress-management/toggle-xmlrpc) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/wp-cli/run` | [Run WP-CLI command](https://developers.ploi.io/wordpress-management/run-wp-cli-command) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/search-replace` | [Search & replace](https://developers.ploi.io/wordpress-management/search-replace) | `not_implemented` |
| `wordpress-management` | `GET` | `/api/servers/{server}/sites/{id}/wordpress/repositories` | [List repositories](https://developers.ploi.io/wordpress-management/list-repositories) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/repositories` | [Install repository](https://developers.ploi.io/wordpress-management/install-repository) | `not_implemented` |
| `wordpress-management` | `PATCH` | `/api/servers/{server}/sites/{id}/wordpress/repositories/{repository}` | [Update repository](https://developers.ploi.io/wordpress-management/update-repository) | `not_implemented` |
| `wordpress-management` | `PATCH` | `/api/servers/{server}/sites/{id}/wordpress/repositories/{repository}/deploy-script` | [Update deploy script](https://developers.ploi.io/wordpress-management/update-repository-deploy-script) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/repositories/{repository}/deploy` | [Deploy repository](https://developers.ploi.io/wordpress-management/deploy-repository) | `not_implemented` |
| `wordpress-management` | `POST` | `/api/servers/{server}/sites/{id}/wordpress/repositories/deploy-all` | [Deploy all repositories](https://developers.ploi.io/wordpress-management/deploy-all-repositories) | `not_implemented` |
| `wordpress-management` | `DELETE` | `/api/servers/{server}/sites/{id}/wordpress/repositories/{repository}` | [Delete repository](https://developers.ploi.io/wordpress-management/delete-repository) | `not_implemented` |
| `fastcgi-cache` | `POST` | `/api/servers/{server}/sites/{site}/fastcgi-cache/enable` | [Enable FastCGI cache](https://developers.ploi.io/fastcgi-cache/enable-fastcgi-cache) | `not_implemented` |
| `fastcgi-cache` | `DELETE` | `/api/servers/{server}/sites/{site}/fastcgi-cache/disable` | [Disable FastCGI cache](https://developers.ploi.io/fastcgi-cache/disable-fastcgi-cache) | `not_implemented` |
| `fastcgi-cache` | `POST` | `/api/servers/{server}/sites/{site}/fastcgi-cache/flush` | [Flush FastCGI cache](https://developers.ploi.io/fastcgi-cache/flush-fastcgi-cache) | `not_implemented` |
| `containers` | `GET` | `/api/servers/{server}/docker/containers` | [List all containers](https://developers.ploi.io/containers/list-containers) | `not_implemented` |
| `containers` | `GET` | `/api/servers/{server}/docker/containers/{container}` | [Retrieve a container](https://developers.ploi.io/containers/get-container) | `not_implemented` |
| `containers` | `POST` | `/api/servers/{server}/docker/containers` | [Create a new container](https://developers.ploi.io/containers/create-container) | `not_implemented` |
| `containers` | `PATCH` | `/api/servers/{server}/docker/containers/{container}` | [Update a container](https://developers.ploi.io/containers/update-container) | `not_implemented` |
| `containers` | `DELETE` | `/api/servers/{server}/docker/containers/{container}` | [Delete a container](https://developers.ploi.io/containers/delete-container) | `not_implemented` |
| `containers` | `POST` | `/api/servers/{server}/docker/containers/{container}/up` | [Start a container](https://developers.ploi.io/containers/start-container) | `not_implemented` |
| `containers` | `POST` | `/api/servers/{server}/docker/containers/{container}/down` | [Stop a container](https://developers.ploi.io/containers/stop-container) | `not_implemented` |
| `containers` | `GET` | `/api/servers/{server}/docker/containers/{container}/logs` | [Get container logs](https://developers.ploi.io/containers/container-logs) | `not_implemented` |
| `containers` | `POST` | `/api/servers/{server}/docker/containers/{container}/site/link` | [Link site to container](https://developers.ploi.io/containers/link-site) | `not_implemented` |
| `containers` | `DELETE` | `/api/servers/{server}/docker/containers/{container}/site/unlink` | [Unlink site from container](https://developers.ploi.io/containers/unlink-site) | `not_implemented` |
| `database` | `GET` | `/api/backups/database` | [List all database backups](https://developers.ploi.io/database/list-database-backups) | `implemented_locally_unverified_live` |
| `database` | `GET` | `/api/backups/database/{databaseBackupId}` | [Get database backup](https://developers.ploi.io/database/get-database-backup) | `implemented_locally_unverified_live` |
| `database` | `POST` | `/api/backups/database` | [Create database backup](https://developers.ploi.io/database/create-database-backup) | `implemented_locally_unverified_live` |
| `database` | `PATCH` | `/api/backups/database/{id}` | [Update database backup](https://developers.ploi.io/database/update-database-backup) | `implemented_locally_unverified_live` |
| `database` | `POST` | `/api/backups/database/{databaseBackupId}/run` | [Run database backup](https://developers.ploi.io/database/run-database-backup) | `implemented_locally_unverified_live` |
| `database` | `DELETE` | `/api/backups/database/{databaseBackupId}` | [Delete database backup](https://developers.ploi.io/database/delete-database-backup) | `implemented_locally_unverified_live` |
| `database` | `GET` | `/api/backups/database/{databaseBackupId}/notification-channels` | [List database backup notification channels](https://developers.ploi.io/database/list-database-backup-notification-channels) | `implemented_locally_unverified_live` |
| `database` | `POST` | `/api/backups/database/{databaseBackupId}/notification-channels` | [Attach database backup notification channel](https://developers.ploi.io/database/attach-database-backup-notification-channel) | `implemented_locally_unverified_live` |
| `database` | `DELETE` | `/api/backups/database/{databaseBackupId}/notification-channels/{channelId}` | [Detach database backup notification channel](https://developers.ploi.io/database/detach-database-backup-notification-channel) | `implemented_locally_unverified_live` |
| `site` | `GET` | `/api/backups/file` | [List site file backups](https://developers.ploi.io/site/list-site-file-backups) | `not_implemented` |
| `site` | `GET` | `/api/backups/file/{fileBackupId}` | [Get site file backup](https://developers.ploi.io/site/get-site-file-backup) | `not_implemented` |
| `site` | `POST` | `/api/backups/file` | [Create site file backup](https://developers.ploi.io/site/create-site-file-backup) | `not_implemented` |
| `site` | `PATCH` | `/api/backups/file/{id}` | [Update site file backup](https://developers.ploi.io/site/update-site-file-backup) | `not_implemented` |
| `site` | `POST` | `/api/backups/file/{fileBackupId}/run` | [Run site file backup](https://developers.ploi.io/site/run-site-file-backup) | `not_implemented` |
| `site` | `DELETE` | `/api/backups/file/{fileBackupId}` | [Delete site file backup](https://developers.ploi.io/site/delete-site-file-backup) | `not_implemented` |
| `site` | `GET` | `/api/backups/file/{fileBackupId}/notification-channels` | [List site file backup notification channels](https://developers.ploi.io/site/list-site-file-backup-notification-channels) | `not_implemented` |
| `site` | `POST` | `/api/backups/file/{fileBackupId}/notification-channels` | [Attach site file backup notification channel](https://developers.ploi.io/site/attach-site-file-backup-notification-channel) | `not_implemented` |
| `site` | `DELETE` | `/api/backups/file/{fileBackupId}/notification-channels/{channelId}` | [Detach site file backup notification channel](https://developers.ploi.io/site/detach-site-file-backup-notification-channel) | `not_implemented` |

## Contrôles de cohérence

- Somme des domaines, cardinalité des opérations, unicité `(méthode, chemin)` et appartenance des URL au sitemap vérifiées par script ; le JSON contient les compteurs et les URL classées.
- Les en-têtes des routes ont été extraits du contenu HTML officiel ; aucun chemin n’est déduit du slug documentaire. Les pages de référence peuvent évoluer : régénérer et comparer au sitemap avant de considérer cette photographie exhaustive.
