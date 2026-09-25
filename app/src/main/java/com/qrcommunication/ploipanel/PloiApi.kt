package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject

internal data class Server(val id: Long, val name: String, val status: String, val ipAddress: String)
internal data class ServerPage(val servers: List<Server>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class MonitorSample(val cpu: String, val ram: String, val disk: String, val load: String, val date: String)
internal data class Site(
    val id: Long, val serverId: Long, val domain: String, val status: String,
    val phpVersion: String, val webDirectory: String, val healthUrl: String, val diskUsage: String,
    val testDomain: String = "", val projectType: String = "", val projectRoot: String = "",
    val systemUser: String = "", val lastDeployAt: String = "", val createdAt: String = "",
    val hasRepository: Boolean = false, val quickDeploy: Boolean = false,
    val zeroDowntimeDeployment: Boolean = false, val disableRobots: Boolean = false,
    val fastcgiCache: Boolean = false
)
internal data class SitePage(val sites: List<Site>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class SiteLogEntry(
    val id: Long, val description: String, val content: String, val type: String,
    val createdAt: String, val createdAtHuman: String
)
internal data class SiteLogPage(val logs: List<SiteLogEntry>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class TestDomain(val id: Long, val domain: String, val testDomain: String, val fullTestDomain: String)
internal data class HorizonQueue(val name: String, val length: Long, val wait: Long, val processes: Long)
internal data class HorizonMaster(val name: String, val pid: String, val status: String, val supervisors: Int)
internal data class HorizonFailedJob(
    val id: String, val connection: String, val queue: String, val name: String,
    val status: String, val exception: String
)
/** Response shapes of GET /servers/{server}/sites/laravel/horizon/{type}. */
internal sealed interface HorizonStatistics {
    data class Stats(
        val failedJobs: Long, val jobsPerMinute: Long, val pausedMasters: Long,
        val processes: Long, val recentJobs: Long, val status: String, val wait: Map<String, Long>
    ) : HorizonStatistics
    data class Workload(val queues: List<HorizonQueue>) : HorizonStatistics
    data class Masters(val masters: List<HorizonMaster>) : HorizonStatistics
    data class Failed(val total: Long, val jobs: List<HorizonFailedJob>) : HorizonStatistics
}
internal data class ProviderOption(val id: String, val name: String, val description: String = "")
internal data class ProviderCredential(
    val id: Long, val name: String, val label: String, val plans: List<ProviderOption>, val regions: List<ProviderOption>
) {
    val displayName: String get() = label.ifBlank { name }
}
internal data class ProviderPage(val providers: List<ProviderCredential>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class UserInfo(
    val name: String, val email: String, val plan: String, val planExpiresAt: String,
    val timezone: String, val country: String, val avatarUrl: String, val createdAt: String,
    val billingDetails: String
)
internal data class BackupConfiguration(val id: Long, val label: String, val type: String, val humanType: String, val createdAt: String)
internal data class BackupConfigurationPage(
    val configurations: List<BackupConfiguration>, val currentPage: Int, val lastPage: Int
) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class NotificationChannel(val id: Long, val type: String, val label: String, val createdAt: String)
internal data class NotificationChannelPage(
    val channels: List<NotificationChannel>, val currentPage: Int, val lastPage: Int
) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class SourceControlProvider(
    val id: Long, val label: String, val name: String, val provider: String, val createdAt: String
) {
    val displayName: String get() = label.ifBlank { name.ifBlank { provider } }
}
internal data class SourceControlPage(
    val providers: List<SourceControlProvider>, val currentPage: Int, val lastPage: Int
) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class SourceControlRepository(val label: String, val name: String, val createdAt: String)
internal data class ServerDetail(
    val id: Long, val status: String, val statusId: Int, val type: String, val databaseType: String,
    val name: String, val ipAddress: String, val internalIp: String, val sshPort: Int,
    val rebootRequired: Boolean, val phpVersion: String, val phpCliVersion: String,
    val mysqlVersion: String, val sitesCount: Int, val monitoring: Boolean, val opcache: Boolean,
    val installedPhpVersions: List<String>, val updatesPackages: Int, val updatesSecurity: Int,
    val description: String, val providerName: String, val createdAt: String,
    val createdHuman: String, val uptimeHuman: String
)
internal data class CustomServerCreation(
    val id: Long, val name: String, val publicKey: String, val sshCommand: String,
    val startInstallationUrl: String, val message: String
)
internal data class ServerLogEntry(
    val description: String, val content: String, val siteId: Long?, val serverId: Long, val createdAt: String
)
internal data class ServerLogPage(val logs: List<ServerLogEntry>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class MonitoredServer(
    val id: Long, val name: String, val ip: String, val url: String, val statistics: List<MonitorSample>
)
/** Repository view of a site (GET/POST/DELETE …/sites/{site}/repository responses). */
internal data class SiteRepository(
    val id: Long, val domain: String, val webDirectory: String, val wordpress: Boolean,
    val laravel: Boolean, val projectRoot: String, val lastDeployAt: String,
    val quickDeploy: Boolean, val createdAt: String,
    val branch: String, val repositoryUser: String, val repositoryName: String, val provider: String
)
/** Toggled quick-deploy response: updated site plus the human-readable confirmation. */
internal data class QuickDeployResult(val site: Site, val message: String)

/** Database entry of GET/POST /api/servers/{server}/databases (type is null in duplicate responses). */
internal data class PloiDatabase(
    val id: Long, val type: String, val name: String, val serverId: Long, val status: String,
    val siteId: Long? = null, val siteDomain: String = "", val createdAt: String = ""
)
internal data class DatabasePage(val databases: List<PloiDatabase>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class DatabaseUser(
    val id: Long, val user: String, val remote: Boolean, val remoteIp: String,
    val readonly: Boolean, val createdAt: String
)
internal data class DatabaseUserPage(val users: List<DatabaseUser>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
/** Duplicate response: the new database plus the human-readable confirmation. */
internal data class DatabaseDuplication(val database: PloiDatabase, val message: String)

/** Database backup entry of GET/PATCH /api/backups/database responses (both documented shapes). */
internal data class DatabaseBackup(
    val id: Long, val status: String, val label: String, val type: String, val typeHuman: String,
    val path: String, val remotePath: String, val locations: String, val interval: Int,
    val tableExclusions: String, val excludedTables: List<String>, val keepBackupAmount: Int,
    val active: Boolean, val compression: String, val deleteOnFail: Boolean, val customName: String,
    val serverId: Long, val serverName: String, val databaseId: Long, val databaseName: String,
    val backupConfigurationId: Long, val backupConfigurationLabel: String,
    val lastBackupAt: String, val nextBackupAt: String, val createdAt: String
)
internal data class DatabaseBackupPage(val backups: List<DatabaseBackup>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
/** Channel attached to a database backup; `location` documents when it is notified. */
internal data class BackupNotificationChannel(
    val id: Long, val type: String, val label: String, val location: String, val createdAt: String
)
/**
 * Site file backup entry (`site`: /backups/file). The documented list shape carries
 * `interval` as text ("daily") and `active` as 0/1, while the update shape carries a
 * numeric interval plus the site, its server and the backup configuration.
 */
internal data class FileBackup(
    val id: Long, val status: String, val label: String, val type: String, val typeHuman: String,
    val path: String, val remotePath: String, val locations: String, val intervalMinutes: Int?,
    val intervalLabel: String, val excluded: List<String>, val keepBackupAmount: Int,
    val active: Boolean, val compression: String, val deleteOnFail: Boolean, val customName: String,
    val localPath: String, val siteId: Long, val siteDomain: String,
    val serverId: Long, val serverName: String,
    val backupConfigurationId: Long, val backupConfigurationLabel: String,
    val lastBackupAt: String, val nextBackupAt: String, val createdAt: String
)
internal data class FileBackupPage(val backups: List<FileBackup>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}

/** Documented value sets for server creation (developers.ploi.io/servers/create-server). */
internal val SERVER_TYPES = setOf("server", "load-balancer", "database-server", "redis-server")
internal val CUSTOM_SERVER_TYPES = SERVER_TYPES + "storage-server"
internal val DATABASE_TYPES = setOf(
    "none", "mysql", "mysql84", "mysql9", "mariadb", "mariadb114",
    "postgresql", "postgresql14", "postgresql15", "postgresql16", "postgresql17", "postgresql18"
)
internal val WEBSERVER_TYPES = setOf("nginx", "nginx-docker")
internal val PHP_VERSIONS = setOf(
    "none", "5.6", "7.0", "7.1", "7.2", "7.3", "7.4", "8.0", "8.1", "8.2", "8.3", "8.4", "8.5"
)
internal val OS_TYPES = setOf("ubuntu-24-04-lts", "ubuntu-26-04-lts", "debian-12", "debian-13")
internal val IP_TYPES = setOf("ipv4", "ipv6", "ipv4-ipv6", "ipv6-ipv4")
internal val DEFAULT_OS_TYPE = "ubuntu-26-04-lts"

/** Documented value sets for site management (developers.ploi.io/sites pages). */
internal val SITE_PROJECT_TYPES = setOf(
    "laravel", "nodejs", "statamic", "craft-cms", "symfony", "wordpress", "octobercms", "cakephp"
)
internal val HORIZON_TYPES = setOf("stats", "workload", "masters", "failed")
internal val SITE_PHP_VERSIONS = PHP_VERSIONS - "none"
/** Documented value sets for deployments/repositories/environment (developers.ploi.io). */
internal val REPOSITORY_PROVIDERS = setOf("bitbucket", "github", "gitlab", "custom")
internal const val DEPLOY_SCRIPT_MAX_LENGTH = 65_535
internal const val ENV_CONTENT_MIN_LENGTH = 2
internal const val DATABASE_NAME_MAX_LENGTH = 64
internal const val DUPLICATE_NAME_MAX_LENGTH = 255
internal const val DUPLICATE_PASSWORD_MAX_LENGTH = 50
/** Documented database backup intervals in minutes; 0 = nightly (create + update documents). */
internal val BACKUP_INTERVALS = setOf(0, 10, 20, 30, 40, 50, 60, 120, 240, 480, 720, 1440, 10080, 43800)
/** Documented intervals (minutes) for POST /backups/file; 0 = nightly. */
internal val FILE_BACKUP_CREATE_INTERVALS = setOf(0, 10, 20, 30, 40, 50, 60, 120, 240, 480, 720, 1440)
/** Documented intervals (minutes) for PATCH /backups/file/{id}; 0 = nightly. */
internal val FILE_BACKUP_UPDATE_INTERVALS = setOf(0, 60, 120, 240, 480, 720, 1440, 10080, 43800)
/** Documented notification locations for database backup channels. */
internal val BACKUP_CHANNEL_LOCATIONS = setOf("before-backup", "after-backup", "failed-backup")
/** Documented shape of the optional next_backup_at schedule (`2025-01-16 03:00:00`). */
private val NEXT_BACKUP_AT_PATTERN = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}(:\d{2})?""")

internal fun validateBackupInterval(interval: Int): Int {
    require(interval in BACKUP_INTERVALS) { "Unsupported backup interval" }
    return interval
}

private fun validateNextBackupAt(value: String): String {
    require(value.isBlank() || NEXT_BACKUP_AT_PATTERN.matches(value)) {
        "Next backup schedule must look like 2025-01-16 03:00:00"
    }
    return value
}

internal fun validateBackupChannelLocation(location: String): String {
    require(location in BACKUP_CHANNEL_LOCATIONS) { "Unsupported notification location" }
    return location
}
/** Database names and creation-time users: alpha-numeric, dashes and underscores, 2-64 (documented). */
private val DATABASE_NAME_PATTERN = Regex("[A-Za-z0-9_-]+")
/**
 * Database user accounts: dashes are documented as forbidden while the official example uses
 * `my_user`, so the accepted charset is alpha-numeric plus underscores.
 */
private val DATABASE_ACCOUNT_PATTERN = Regex("[A-Za-z0-9_]+")

internal fun validateDatabaseName(name: String): String {
    require(name.length in 2..DATABASE_NAME_MAX_LENGTH && DATABASE_NAME_PATTERN.matches(name)) {
        "Database name must be 2-$DATABASE_NAME_MAX_LENGTH alpha-numeric characters, dashes or underscores"
    }
    return name
}
private val SCHEDULED_DEPLOY_PATTERN = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
private val ROOT_DOMAIN_PATTERN = Regex("\\S+")
private val WEB_DIRECTORY_PATTERN = Regex("[a-zA-Z0-9/]+")

internal fun validateRootDomain(domain: String): String {
    require(domain.isNotBlank() && domain.length <= 100 && ROOT_DOMAIN_PATTERN.matches(domain)) {
        "Invalid root domain"
    }
    return domain
}

internal fun validateWebDirectory(directory: String): String {
    require(directory.length <= 50 && WEB_DIRECTORY_PATTERN.matches(directory)) { "Invalid web directory" }
    return directory
}
private val SERVER_NAME_PATTERN = Regex("[A-Za-z0-9-]+")
private val IPV4_PATTERN = Regex("""\d{1,3}(\.\d{1,3}){3}""")

internal fun validateServerName(name: String): String {
    require(name.isNotBlank() && name.length <= 50 && SERVER_NAME_PATTERN.matches(name)) {
        "Server name must be alpha-numeric with dashes only"
    }
    return name
}

internal fun validateIpAddress(ip: String): String {
    require((IPV4_PATTERN.matches(ip) && ip.split(".").all { it.toInt() in 0..255 }) || ip.contains(':')) {
        "Invalid IP address"
    }
    return ip
}

internal fun validateSshPort(port: Int): Int {
    require(port in 1..65_535) { "SSH port out of range" }
    return port
}

internal fun validateWebhookUrl(url: String): String {
    require(url.startsWith("https://") && url.length > "https://".length) { "Webhook URL must use HTTPS" }
    return url
}

private fun optionalName(name: String): String {
    if (name.isBlank()) return ""
    return validateServerName(name)
}

/** Validated payload for POST /api/servers (provider-based creation). */
internal data class CreateServerRequest(
    val plan: String,
    val region: String,
    val credential: Long,
    val type: String,
    val databaseType: String,
    val webserverType: String,
    val phpVersion: String,
    val name: String = "",
    val osType: String = DEFAULT_OS_TYPE,
    val description: String = "",
    val installMonitoring: Boolean = false,
    val ipType: String = "ipv4",
    val webhookUrl: String = ""
) {
    init {
        require(plan.isNotBlank()) { "Plan is required" }
        require(region.isNotBlank()) { "Region is required" }
        require(credential > 0) { "Credential must be a valid provider ID" }
        require(type in SERVER_TYPES) { "Unsupported server type" }
        require(databaseType in DATABASE_TYPES) { "Unsupported database type" }
        require(webserverType in WEBSERVER_TYPES) { "Unsupported webserver type" }
        require(phpVersion in PHP_VERSIONS) { "Unsupported PHP version" }
        optionalName(name)
        require(osType in OS_TYPES) { "Unsupported OS type" }
        require(ipType in IP_TYPES) { "Unsupported IP type" }
        if (webhookUrl.isNotBlank()) validateWebhookUrl(webhookUrl)
    }

    fun toJson(): String = JSONObject().apply {
        put("plan", plan)
        put("region", region)
        put("credential", credential)
        put("type", type)
        put("database_type", databaseType)
        put("webserver_type", webserverType)
        put("php_version", phpVersion)
        if (name.isNotBlank()) put("name", name)
        put("os_type", osType)
        if (description.isNotBlank()) put("description", description)
        if (installMonitoring) put("install_monitoring", true)
        put("ip_type", ipType)
        if (webhookUrl.isNotBlank()) put("webhook_url", webhookUrl)
    }.toString()
}

/** Validated payload for POST /api/servers/custom (bring-your-own server). */
internal data class CreateCustomServerRequest(
    val type: String,
    val ip: String,
    val sshPort: Int,
    val databaseType: String,
    val phpVersion: String,
    val name: String = "",
    val osType: String = DEFAULT_OS_TYPE,
    val description: String = ""
) {
    init {
        require(type in CUSTOM_SERVER_TYPES) { "Unsupported server type" }
        validateIpAddress(ip)
        validateSshPort(sshPort)
        require(databaseType in DATABASE_TYPES) { "Unsupported database type" }
        require(phpVersion in PHP_VERSIONS) { "Unsupported PHP version" }
        optionalName(name)
        require(osType in OS_TYPES) { "Unsupported OS type" }
    }

    fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("ip", ip)
        put("ssh_port", sshPort)
        put("database_type", databaseType)
        put("php_version", phpVersion)
        if (name.isNotBlank()) put("name", name)
        put("os_type", osType)
        if (description.isNotBlank()) put("description", description)
    }.toString()
}

/** Validated payload for POST /api/servers/{server}/sites. */
internal data class CreateSiteRequest(
    val rootDomain: String,
    val webDirectory: String,
    val projectRoot: String = "",
    val projectType: String = "",
    val systemUser: String = "",
    val webserverTemplate: Long? = null,
    val webhookUrl: String = ""
) {
    init {
        validateRootDomain(rootDomain)
        validateWebDirectory(webDirectory)
        require(projectRoot.length <= 50) { "Project root too long" }
        require(projectType.isEmpty() || projectType in SITE_PROJECT_TYPES) { "Unsupported project type" }
        require(webserverTemplate == null || webserverTemplate > 0) { "Invalid webserver template" }
        if (webhookUrl.isNotBlank()) validateWebhookUrl(webhookUrl)
    }

    fun toJson(): String = JSONObject().apply {
        put("root_domain", rootDomain)
        put("web_directory", webDirectory)
        if (projectRoot.isNotBlank()) put("project_root", projectRoot)
        if (projectType.isNotBlank()) put("project_type", projectType)
        if (systemUser.isNotBlank()) put("system_user", systemUser)
        webserverTemplate?.let { put("webserver_template", it) }
        if (webhookUrl.isNotBlank()) put("webhook_url", webhookUrl)
    }.toString()
}

/** Validated payload for POST /api/servers/{server}/sites/{site}/repository. */
internal data class InstallRepositoryRequest(
    val provider: String,
    val branch: String,
    val name: String,
    val sourceProviderId: Long? = null,
    val installComposer: Boolean = false
) {
    init {
        require(provider in REPOSITORY_PROVIDERS) { "Unsupported repository provider" }
        require(branch.isNotBlank()) { "Branch is required" }
        require(name.isNotBlank()) { "Repository name is required" }
        require(provider != "custom" || name.endsWith(".git")) {
            "Custom repositories must be a GIT URL ending with .git"
        }
        require(sourceProviderId == null || sourceProviderId > 0) { "Invalid source provider ID" }
    }

    fun toJson(): String = JSONObject().apply {
        put("provider", provider)
        put("branch", branch)
        put("name", name)
        sourceProviderId?.let { put("source_provider_id", it) }
        if (installComposer) put("install_composer", true)
    }.toString()
}

/** Validated payload for POST /api/servers/{server}/databases. */
internal data class CreateDatabaseRequest(
    val name: String,
    val user: String = "",
    val password: String = "",
    val description: String = "",
    val siteId: Long? = null
) {
    init {
        validateDatabaseName(name)
        // Documented rules for the optional creation-time user mirror the database name rules.
        if (user.isNotEmpty()) validateDatabaseName(user)
        require(password.isEmpty() || password.isNotBlank()) { "Password must not be blank" }
        require(siteId == null || siteId > 0) { "Invalid site ID" }
    }

    fun toJson(): String = JSONObject().apply {
        put("name", name)
        if (user.isNotEmpty()) put("user", user)
        if (password.isNotEmpty()) put("password", password)
        if (description.isNotBlank()) put("description", description)
        siteId?.let { put("site_id", it) }
    }.toString()
}

/** Validated payload for POST /api/servers/{server}/databases/{database}/users. */
internal data class CreateDatabaseUserRequest(
    val user: String,
    val password: String,
    val remote: Boolean = false,
    val remoteIp: String = "",
    val readonly: Boolean = false
) {
    init {
        require(user.isNotBlank() && DATABASE_ACCOUNT_PATTERN.matches(user)) {
            "Database user must be alpha-numeric with underscores, no dashes"
        }
        require(password.isNotBlank()) { "Password is required" }
        // Documented: remote_ip is required when remote is true; "%" wildcards are legitimate values.
        require(!remote || remoteIp.isNotBlank()) { "Remote IP is required for remote users" }
    }

    fun toJson(): String = JSONObject().apply {
        put("user", user)
        put("password", password)
        if (remote) {
            put("remote", true)
            put("remote_ip", remoteIp)
        }
        if (readonly) put("readonly", true)
    }.toString()
}

/** Validated payload for POST /api/backups/database (documented required + optional fields). */
internal data class CreateDatabaseBackupRequest(
    val backupConfiguration: Long,
    val server: Long,
    val databases: List<Long>,
    val interval: Int,
    val tableExclusions: String = "",
    val locations: String = "",
    val path: String = "",
    val keepBackupAmount: Int? = null,
    val customName: String = "",
    val password: String = "",
    val nextBackupAt: String = "",
    val deleteOnFail: Boolean = false
) {
    init {
        require(backupConfiguration > 0) { "Backup configuration must be a valid ID" }
        require(server > 0) { "Server must be a valid ID" }
        require(databases.isNotEmpty() && databases.all { it > 0 }) { "At least one valid database ID is required" }
        validateBackupInterval(interval)
        require(keepBackupAmount == null || keepBackupAmount >= 0) { "Keep backup amount must not be negative" }
        validateNextBackupAt(nextBackupAt)
    }

    fun toJson(): String = JSONObject().apply {
        put("backup_configuration", backupConfiguration)
        put("server", server)
        put("databases", JSONArray(databases))
        put("interval", interval)
        if (tableExclusions.isNotBlank()) put("table_exclusions", tableExclusions)
        if (locations.isNotBlank()) put("locations", locations)
        if (path.isNotBlank()) put("path", path)
        keepBackupAmount?.let { put("keep_backup_amount", it) }
        if (customName.isNotBlank()) put("custom_name", customName)
        if (password.isNotEmpty()) put("password", password)
        if (nextBackupAt.isNotBlank()) put("next_backup_at", nextBackupAt)
        if (deleteOnFail) put("deleteOnFail", true)
    }.toString()
}

/** Validated payload for PATCH /api/backups/database/{id} (interval + keep amount required). */
internal data class UpdateDatabaseBackupRequest(
    val interval: Int,
    val keepBackupAmount: Int,
    val deleteOnFail: Boolean? = null,
    val customName: String = "",
    val path: String = "",
    val compression: String = "",
    val excluded: List<String> = emptyList(),
    val locations: String = "",
    val nextBackupAt: String = ""
) {
    init {
        validateBackupInterval(interval)
        require(keepBackupAmount >= 0) { "Keep backup amount must not be negative" }
        require(excluded.none { it.isBlank() }) { "Excluded tables must not be blank" }
        validateNextBackupAt(nextBackupAt)
    }

    fun toJson(): String = JSONObject().apply {
        put("interval", interval)
        put("keep_backup_amount", keepBackupAmount)
        deleteOnFail?.let { put("deleteOnFail", it) }
        if (customName.isNotBlank()) put("custom_name", customName)
        if (path.isNotBlank()) put("path", path)
        if (compression.isNotBlank()) put("compression", compression)
        if (excluded.isNotEmpty()) put("excluded", JSONArray(excluded))
        if (locations.isNotBlank()) put("locations", locations)
        if (nextBackupAt.isNotBlank()) put("next_backup_at", nextBackupAt)
    }.toString()
}

private fun validateAbsoluteBackupPath(path: String, field: String): String {
    require(path.startsWith("/")) { "$field must be an absolute path" }
    return path
}

/**
 * Validated payload for POST /api/backups/file: `path` is the documented object mapping
 * each selected site ID to the absolute path to back up.
 */
internal data class CreateFileBackupRequest(
    val backupConfiguration: Long,
    val server: Long,
    val sites: List<Long>,
    val interval: Int,
    val paths: Map<Long, String>,
    val locations: String = "",
    val keepBackupAmount: Int? = null,
    val customName: String = "",
    val localPath: String = "",
    val password: String = "",
    val nextBackupAt: String = "",
    val deleteOnFail: Boolean = false
) {
    init {
        require(backupConfiguration > 0) { "Backup configuration must be a valid ID" }
        require(server > 0) { "Server must be a valid ID" }
        require(sites.isNotEmpty() && sites.all { it > 0 }) { "At least one valid site ID is required" }
        require(interval in FILE_BACKUP_CREATE_INTERVALS) { "Unsupported file backup interval" }
        require(paths.keys.containsAll(sites)) { "Every selected site needs a backup path" }
        paths.values.forEach { validateAbsoluteBackupPath(it, "Backup path") }
        require(keepBackupAmount == null || keepBackupAmount >= 0) { "Keep backup amount must not be negative" }
        if (localPath.isNotBlank()) validateAbsoluteBackupPath(localPath, "Local path")
        validateNextBackupAt(nextBackupAt)
    }

    fun toJson(): String = JSONObject().apply {
        put("backup_configuration", backupConfiguration)
        put("server", server)
        put("sites", JSONArray(sites))
        put("interval", interval)
        put("path", JSONObject(paths.mapKeys { (siteId, _) -> siteId.toString() }))
        if (locations.isNotBlank()) put("locations", locations)
        keepBackupAmount?.let { put("keep_backup_amount", it) }
        if (customName.isNotBlank()) put("custom_name", customName)
        if (localPath.isNotBlank()) put("local_path", localPath)
        if (password.isNotEmpty()) put("password", password)
        if (nextBackupAt.isNotBlank()) put("next_backup_at", nextBackupAt)
        if (deleteOnFail) put("deleteOnFail", true)
    }.toString()
}

/**
 * Validated payload for PATCH /api/backups/file/{id}: interval, keep amount and path are
 * documented as required; the path must start with /home/system_user/root_domain.
 */
internal data class UpdateFileBackupRequest(
    val interval: Int,
    val keepBackupAmount: Int,
    val path: String,
    val deleteOnFail: Boolean? = null,
    val customName: String = "",
    val localPath: String = "",
    val compression: String = "",
    val excluded: List<String> = emptyList(),
    val locations: String = "",
    val nextBackupAt: String = ""
) {
    init {
        require(interval in FILE_BACKUP_UPDATE_INTERVALS) { "Unsupported file backup interval" }
        require(keepBackupAmount >= 0) { "Keep backup amount must not be negative" }
        require(path.startsWith("/home/")) { "Path must start with /home/system_user/root_domain" }
        require(excluded.none { it.isBlank() }) { "Excluded entries must not be blank" }
        if (localPath.isNotBlank()) validateAbsoluteBackupPath(localPath, "Local path")
        validateNextBackupAt(nextBackupAt)
    }

    fun toJson(): String = JSONObject().apply {
        put("interval", interval)
        put("keep_backup_amount", keepBackupAmount)
        put("path", path)
        deleteOnFail?.let { put("deleteOnFail", it) }
        if (customName.isNotBlank()) put("custom_name", customName)
        if (localPath.isNotBlank()) put("local_path", localPath)
        if (compression.isNotBlank()) put("compression", compression)
        if (excluded.isNotEmpty()) put("excluded", JSONArray(excluded))
        if (locations.isNotBlank()) put("locations", locations)
        if (nextBackupAt.isNotBlank()) put("next_backup_at", nextBackupAt)
    }.toString()
}

internal class PloiHttpException(val status: Int, val retryAfterSeconds: String? = null) : Exception("Ploi HTTP $status")

/** Typed API surface. Never persist or log a bearer token. */
internal object PloiApi {
    fun validateToken(token: String): String {
        require(token.isNotBlank() && token == token.trim() && !token.contains('\r') && !token.contains('\n')) {
            "Invalid bearer token"
        }
        return token
    }

    fun validatePageSize(size: Int): Int {
        require(size in 1..50) { "Ploi supports 1 to 50 items per request" }
        return size
    }

    /** Validated (currentPage, lastPage) pair shared by every paginated collection. */
    private fun pageMeta(root: JSONObject): Pair<Int, Int> {
        val meta = root.getJSONObject("meta")
        val page = meta.getInt("current_page")
        val lastPage = meta.getInt("last_page")
        require(page >= 1 && lastPage >= 1 && page <= lastPage) { "Invalid pagination metadata" }
        return page to lastPage
    }

    fun parseServers(json: String): ServerPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        val servers = (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            Server(item.getLong("id"), item.getString("name"), item.optString("status"), item.optString("ip_address"))
        }
        return ServerPage(servers, page, lastPage)
    }

    fun parseMonitoring(json: String): MonitorSample? {
        val data = JSONObject(json).getJSONArray("data")
        if (data.length() == 0) return null
        return parseMonitorSample((0 until data.length()).map(data::getJSONObject).maxBy { it.getString("date") })
    }

    private fun parseMonitorSample(sample: JSONObject) = MonitorSample(
        sample.optString("cpu"), sample.optString("ram"), sample.optString("disk"),
        sample.optString("load_average"), sample.getString("date")
    )

    fun validateResourceId(id: Long): Long {
        require(id > 0) { "Resource ID must be positive" }
        return id
    }

    fun parseSites(json: String): SitePage {
        val root = JSONObject(json)
        val (page, lastPage) = pageMeta(root)
        val data = root.getJSONArray("data")
        return SitePage((0 until data.length()).map { parseSiteEntry(data.getJSONObject(it)) }, page, lastPage)
    }

    fun parseSite(json: String): Site = parseSiteEntry(JSONObject(json).getJSONObject("data"))

    private fun parseSiteEntry(item: JSONObject): Site = Site(
        id = item.getLong("id"),
        serverId = item.getLong("server_id"),
        domain = item.getString("domain"),
        status = item.optString("status"),
        phpVersion = item.opt("php_version")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty(),
        webDirectory = item.optString("web_directory"),
        healthUrl = if (item.isNull("health_url")) "" else item.getString("health_url"),
        diskUsage = item.optJSONObject("disk_usage")?.optString("human").orEmpty(),
        testDomain = nullableString(item, "test_domain"),
        projectType = nullableString(item, "project_type"),
        projectRoot = item.optString("project_root"),
        systemUser = nullableString(item, "system_user"),
        lastDeployAt = nullableString(item, "last_deploy_at"),
        createdAt = item.optString("created_at"),
        hasRepository = item.optBoolean("has_repository", false),
        quickDeploy = item.optBoolean("quick_deploy", false),
        zeroDowntimeDeployment = item.optBoolean("zero_downtime_deployment", false),
        disableRobots = item.optBoolean("disable_robots", false),
        fastcgiCache = item.optBoolean("fastcgi_cache", false)
    )

    fun sites(token: String, serverId: Long, page: Int = 1, perPage: Int = 15): SitePage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow { parseSites(get("/servers/${validateResourceId(serverId)}/sites?page=$page&per_page=${validatePageSize(perPage)}", token)) }
    }

    fun site(token: String, serverId: Long, siteId: Long): Site = parseOrThrow {
        parseSite(get("/servers/${validateResourceId(serverId)}/sites/${validateResourceId(siteId)}", token))
    }

    // ---- Sites domain: full documented read + write surface ----

    private fun sitePath(serverId: Long, siteId: Long): String =
        "/servers/${validateResourceId(serverId)}/sites/${validateResourceId(siteId)}"

    fun createSite(token: String, serverId: Long, request: CreateSiteRequest): Site = parseOrThrow {
        parseSite(write("POST", "/servers/${validateResourceId(serverId)}/sites", token, request.toJson()))
    }

    /**
     * PATCH /sites/{site}: only present parameters are sent (root_domain, zero_downtime_deployment,
     * disable_robots per the update-site and robot-access documents, which share this route).
     */
    fun updateSite(
        token: String, serverId: Long, siteId: Long,
        rootDomain: String = "", zeroDowntimeDeployment: Boolean? = null, disableRobots: Boolean? = null
    ): Site {
        require(rootDomain.isNotBlank() || zeroDowntimeDeployment != null || disableRobots != null) {
            "At least one site attribute to update"
        }
        if (rootDomain.isNotBlank()) validateRootDomain(rootDomain)
        val body = JSONObject().apply {
            if (rootDomain.isNotBlank()) put("root_domain", rootDomain)
            zeroDowntimeDeployment?.let { put("zero_downtime_deployment", it) }
            disableRobots?.let { put("disable_robots", it) }
        }.toString()
        return parseOrThrow { parseSite(write("PATCH", sitePath(serverId, siteId), token, body)) }
    }

    /** Dedicated documented robot-access operation on the same PATCH route. */
    fun updateRobotAccess(token: String, serverId: Long, siteId: Long, disableRobots: Boolean): Site =
        parseOrThrow {
            val body = JSONObject().put("disable_robots", disableRobots).toString()
            parseSite(write("PATCH", sitePath(serverId, siteId), token, body))
        }

    fun deleteSite(token: String, serverId: Long, siteId: Long): String = parseOrThrow {
        parseMessage(write("DELETE", sitePath(serverId, siteId), token, null))
    }

    private fun parseSiteLogEntry(item: JSONObject) = SiteLogEntry(
        id = item.getLong("id"),
        description = item.getString("description"),
        content = item.optString("content"),
        type = nullableString(item, "type"),
        createdAt = item.optString("created_at"),
        createdAtHuman = nullableString(item, "created_at_human")
    )

    fun parseSiteLogs(json: String): SiteLogPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return SiteLogPage((0 until data.length()).map { parseSiteLogEntry(data.getJSONObject(it)) }, page, lastPage)
    }

    fun parseSiteLog(json: String): SiteLogEntry = parseSiteLogEntry(JSONObject(json).getJSONObject("data"))

    fun siteLogs(token: String, serverId: Long, siteId: Long, page: Int = 1, perPage: Int = 15): SiteLogPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseSiteLogs(get("${sitePath(serverId, siteId)}/log?page=$page&per_page=${validatePageSize(perPage)}", token))
        }
    }

    fun siteLog(token: String, serverId: Long, siteId: Long, logId: Long): SiteLogEntry = parseOrThrow {
        parseSiteLog(get("${sitePath(serverId, siteId)}/log/${validateResourceId(logId)}", token))
    }

    fun parseTestDomain(json: String): TestDomain {
        val data = JSONObject(json).getJSONObject("data")
        return TestDomain(
            id = data.getLong("id"),
            domain = data.getString("domain"),
            testDomain = nullableString(data, "test_domain"),
            fullTestDomain = nullableString(data, "full_test_domain")
        )
    }

    fun testDomain(token: String, serverId: Long, siteId: Long): TestDomain = parseOrThrow {
        parseTestDomain(get("${sitePath(serverId, siteId)}/test-domain", token))
    }

    fun enableTestDomain(token: String, serverId: Long, siteId: Long): TestDomain = parseOrThrow {
        parseTestDomain(write("POST", "${sitePath(serverId, siteId)}/test-domain", token, null))
    }

    fun disableTestDomain(token: String, serverId: Long, siteId: Long): TestDomain = parseOrThrow {
        parseTestDomain(write("DELETE", "${sitePath(serverId, siteId)}/test-domain", token, null))
    }

    fun suspendSite(token: String, serverId: Long, siteId: Long, reason: String = ""): Site = parseOrThrow {
        val body = JSONObject().apply { if (reason.isNotBlank()) put("reason", reason) }.toString()
        parseSite(write("POST", "${sitePath(serverId, siteId)}/suspend", token, body))
    }

    fun resumeSite(token: String, serverId: Long, siteId: Long): Site = parseOrThrow {
        parseSite(write("POST", "${sitePath(serverId, siteId)}/resume", token, null))
    }

    fun parseHorizonStatistics(type: String, json: String): HorizonStatistics {
        require(type in HORIZON_TYPES) { "Unsupported Horizon statistics type" }
        val data = JSONObject(json).get("data")
        return when (type) {
            "workload" -> {
                val entries = (data as org.json.JSONArray)
                HorizonStatistics.Workload((0 until entries.length()).map { index ->
                    entries.getJSONObject(index).let { item ->
                        HorizonQueue(
                            name = item.getString("name"), length = item.optLong("length"),
                            wait = item.optLong("wait"), processes = item.optLong("processes")
                        )
                    }
                })
            }
            "masters" -> {
                val masters = (data as JSONObject)
                HorizonStatistics.Masters(masters.keys().asSequence().map { key ->
                    masters.getJSONObject(key).let { item ->
                        HorizonMaster(
                            name = item.optString("name", key), pid = item.optString("pid"),
                            status = item.optString("status"),
                            supervisors = item.optJSONArray("supervisors")?.length() ?: 0
                        )
                    }
                }.toList())
            }
            "failed" -> {
                val failed = (data as JSONObject)
                val jobs = failed.optJSONArray("jobs")
                HorizonStatistics.Failed(
                    total = failed.optLong("total"),
                    jobs = jobs?.let { array ->
                        (0 until array.length()).map { index ->
                            array.getJSONObject(index).let { item ->
                                HorizonFailedJob(
                                    id = item.optString("id"), connection = item.optString("connection"),
                                    queue = item.optString("queue"), name = item.optString("name"),
                                    status = item.optString("status"), exception = item.optString("exception")
                                )
                            }
                        }
                    }.orEmpty()
                )
            }
            else -> {
                val stats = (data as JSONObject)
                val wait = stats.optJSONObject("wait")
                HorizonStatistics.Stats(
                    failedJobs = stats.optLong("failedJobs"),
                    jobsPerMinute = stats.optLong("jobsPerMinute"),
                    pausedMasters = stats.optLong("pausedMasters"),
                    processes = stats.optLong("processes"),
                    recentJobs = stats.optLong("recentJobs"),
                    status = stats.optString("status"),
                    wait = wait?.let { queues ->
                        queues.keys().asSequence().associateWith { queues.optLong(it) }
                    }.orEmpty()
                )
            }
        }
    }

    fun horizonStatistics(token: String, serverId: Long, type: String = "stats"): HorizonStatistics {
        require(type in HORIZON_TYPES) { "Unsupported Horizon statistics type" }
        return parseOrThrow {
            parseHorizonStatistics(
                type,
                get("/servers/${validateResourceId(serverId)}/sites/laravel/horizon/$type", token)
            )
        }
    }

    fun parseConfigurationContent(json: String): String = JSONObject(json).getString("content")

    fun nginxConfiguration(token: String, serverId: Long, siteId: Long): String = parseOrThrow {
        parseConfigurationContent(get("${sitePath(serverId, siteId)}/nginx-configuration", token))
    }

    fun updateNginxConfiguration(token: String, serverId: Long, siteId: Long, content: String): String {
        require(content.isNotBlank()) { "NGINX configuration content required" }
        return parseOrThrow {
            val body = JSONObject().put("content", content).toString()
            parseMessage(write("PATCH", "${sitePath(serverId, siteId)}/nginx-configuration", token, body))
        }
    }

    fun cloneSite(token: String, serverId: Long, siteId: Long, targetServerId: Long, domain: String = ""): String {
        validateResourceId(targetServerId)
        if (domain.isNotBlank()) validateRootDomain(domain)
        val body = JSONObject().apply {
            put("clone_to_server", targetServerId)
            if (domain.isNotBlank()) put("domain", domain)
        }.toString()
        return parseOrThrow { parseMessage(write("POST", "${sitePath(serverId, siteId)}/clone", token, body)) }
    }

    fun changeSitePhpVersion(token: String, serverId: Long, siteId: Long, phpVersion: String): Site {
        require(phpVersion in SITE_PHP_VERSIONS) { "Unsupported PHP version" }
        return parseOrThrow {
            val body = JSONObject().put("php_version", phpVersion).toString()
            parseSite(write("POST", "${sitePath(serverId, siteId)}/php-version", token, body))
        }
    }

    fun resetSitePermissions(token: String, serverId: Long, siteId: Long): Site = parseOrThrow {
        parseSite(write("POST", "${sitePath(serverId, siteId)}/permission-reset", token, null))
    }

    // ---- Deployments domain: deploy script + deploy triggers ----

    fun parseDeployScript(json: String): String = JSONObject(json).getString("deploy_script")

    fun deployScript(token: String, serverId: Long, siteId: Long): String = parseOrThrow {
        parseDeployScript(get("${sitePath(serverId, siteId)}/deploy/script", token))
    }

    fun updateDeployScript(token: String, serverId: Long, siteId: Long, script: String): String {
        require(script.isNotBlank() && script.length <= DEPLOY_SCRIPT_MAX_LENGTH) {
            "Deploy script must be 1 to $DEPLOY_SCRIPT_MAX_LENGTH characters"
        }
        return parseOrThrow {
            val body = JSONObject().put("deploy_script", script).toString()
            parseMessage(write("PATCH", "${sitePath(serverId, siteId)}/deploy/script", token, body))
        }
    }

    /** POST /sites/{id}/deploy; optional scheduled time uses the documented 'yyyy-MM-dd HH:mm' format. */
    fun deploySite(
        token: String, serverId: Long, siteId: Long,
        scheduled: String = "", variables: Map<String, String> = emptyMap()
    ): String {
        require(scheduled.isBlank() || SCHEDULED_DEPLOY_PATTERN.matches(scheduled)) {
            "Scheduled deployment must use 'yyyy-MM-dd HH:mm'"
        }
        variables.keys.forEach { require(it.isNotBlank()) { "Variable names must not be blank" } }
        val body = JSONObject().apply {
            if (scheduled.isNotBlank()) put("scheduled", scheduled)
            if (variables.isNotEmpty()) put("variables", JSONObject(variables))
        }.toString()
        return parseOrThrow { parseMessage(write("POST", "${sitePath(serverId, siteId)}/deploy", token, body)) }
    }

    fun deployToProduction(token: String, serverId: Long, siteId: Long): String = parseOrThrow {
        parseMessage(write("POST", "${sitePath(serverId, siteId)}/deploy-to-production", token, JSONObject().toString()))
    }

    // ---- Repositories domain ----

    private fun repositoryPath(serverId: Long, siteId: Long): String = "${sitePath(serverId, siteId)}/repository"

    private fun parseSiteRepositoryEntry(item: JSONObject): SiteRepository {
        val repository = item.optJSONObject("repository")
        return SiteRepository(
            id = item.getLong("id"),
            domain = item.getString("domain"),
            webDirectory = item.optString("web_directory"),
            wordpress = item.optBoolean("wordpress", false),
            laravel = item.optBoolean("laravel", false),
            projectRoot = item.optString("project_root"),
            lastDeployAt = nullableString(item, "last_deploy_at"),
            quickDeploy = item.optBoolean("quick_deploy", false),
            createdAt = item.optString("created_at"),
            branch = repository?.optString("branch").orEmpty(),
            repositoryUser = repository?.optString("user").orEmpty(),
            repositoryName = repository?.optString("name").orEmpty(),
            provider = repository?.optString("provider").orEmpty()
        )
    }

    fun parseSiteRepository(json: String): SiteRepository =
        parseSiteRepositoryEntry(JSONObject(json).getJSONObject("data"))

    fun repository(token: String, serverId: Long, siteId: Long): SiteRepository = parseOrThrow {
        parseSiteRepository(get(repositoryPath(serverId, siteId), token))
    }

    fun installRepository(token: String, serverId: Long, siteId: Long, request: InstallRepositoryRequest): SiteRepository =
        parseOrThrow { parseSiteRepository(write("POST", repositoryPath(serverId, siteId), token, request.toJson())) }

    fun enableCustomDeployments(token: String, serverId: Long, siteId: Long, script: String = ""): SiteRepository {
        val body = JSONObject().apply { if (script.isNotBlank()) put("script", script) }.toString()
        return parseOrThrow {
            parseSiteRepository(write("POST", "${repositoryPath(serverId, siteId)}/custom-deployments", token, body))
        }
    }

    fun deleteRepository(token: String, serverId: Long, siteId: Long): SiteRepository = parseOrThrow {
        parseSiteRepository(write("DELETE", repositoryPath(serverId, siteId), token, null))
    }

    fun parseQuickDeploy(json: String): QuickDeployResult {
        val root = JSONObject(json)
        return QuickDeployResult(parseSiteEntry(root.getJSONObject("data")), root.optString("message"))
    }

    fun toggleQuickDeploy(token: String, serverId: Long, siteId: Long): QuickDeployResult = parseOrThrow {
        parseQuickDeploy(write("POST", "${repositoryPath(serverId, siteId)}/quick-deploy", token, JSONObject().toString()))
    }

    // ---- Environment domain: .env read/update (same {content} envelope as NGINX) ----

    fun environmentFile(token: String, serverId: Long, siteId: Long): String = parseOrThrow {
        parseConfigurationContent(get("${sitePath(serverId, siteId)}/env", token))
    }

    fun updateEnvironmentFile(token: String, serverId: Long, siteId: Long, content: String): String {
        require(content.length >= ENV_CONTENT_MIN_LENGTH) {
            "Environment file content must be at least $ENV_CONTENT_MIN_LENGTH characters"
        }
        return parseOrThrow {
            val body = JSONObject().put("content", content).toString()
            parseMessage(write("PATCH", "${sitePath(serverId, siteId)}/env", token, body))
        }
    }

    // ---- Databases + database-users domains ----

    private fun databasesPath(serverId: Long): String = "/servers/${validateResourceId(serverId)}/databases"

    private fun databasePath(serverId: Long, databaseId: Long): String =
        "${databasesPath(serverId)}/${validateResourceId(databaseId)}"

    private fun parseDatabaseEntry(item: JSONObject): PloiDatabase {
        val site = item.optJSONObject("site")
        return PloiDatabase(
            id = item.getLong("id"),
            type = nullableString(item, "type"),
            name = item.getString("name"),
            serverId = item.getLong("server_id"),
            status = item.optString("status"),
            siteId = site?.optLong("id")?.takeIf { it > 0 },
            siteDomain = site?.optString("root_domain").orEmpty(),
            createdAt = item.optString("created_at")
        )
    }

    fun parseDatabases(json: String): DatabasePage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return DatabasePage((0 until data.length()).map { parseDatabaseEntry(data.getJSONObject(it)) }, page, lastPage)
    }

    fun parseDatabase(json: String): PloiDatabase = parseDatabaseEntry(JSONObject(json).getJSONObject("data"))

    fun databases(token: String, serverId: Long, page: Int = 1, perPage: Int = 15): DatabasePage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseDatabases(get("${databasesPath(serverId)}?page=$page&per_page=${validatePageSize(perPage)}", token))
        }
    }

    fun database(token: String, serverId: Long, databaseId: Long): PloiDatabase = parseOrThrow {
        parseDatabase(get(databasePath(serverId, databaseId), token))
    }

    fun createDatabase(token: String, serverId: Long, request: CreateDatabaseRequest): PloiDatabase = parseOrThrow {
        parseDatabase(write("POST", databasesPath(serverId), token, request.toJson()))
    }

    /** DELETE /databases/{id}: documented response body is empty, so nothing is parsed. */
    fun deleteDatabase(token: String, serverId: Long, databaseId: Long) {
        write("DELETE", databasePath(serverId, databaseId), token, null)
    }

    /** POST /databases/acknowledge: registers a database created outside Ploi. */
    fun acknowledgeDatabase(token: String, serverId: Long, name: String): PloiDatabase {
        validateDatabaseName(name)
        return parseOrThrow {
            val body = JSONObject().put("name", name).toString()
            parseDatabase(write("POST", "${databasesPath(serverId)}/acknowledge", token, body))
        }
    }

    /** DELETE /databases/{id}/forget: removes Ploi's record without deleting server-side; empty body. */
    fun forgetDatabase(token: String, serverId: Long, databaseId: Long) {
        write("DELETE", "${databasePath(serverId, databaseId)}/forget", token, null)
    }

    fun parseDatabaseDuplication(json: String): DatabaseDuplication {
        val root = JSONObject(json)
        return DatabaseDuplication(parseDatabaseEntry(root.getJSONObject("data")), root.optString("message"))
    }

    /** POST /databases/{database}/duplicate: name is documented as free text (max 255), password max 50. */
    fun duplicateDatabase(
        token: String, serverId: Long, databaseId: Long,
        name: String, user: String = "", password: String = ""
    ): DatabaseDuplication {
        require(name.isNotBlank() && name.length <= DUPLICATE_NAME_MAX_LENGTH) {
            "New database name must be 1 to $DUPLICATE_NAME_MAX_LENGTH characters"
        }
        require(user.length <= DUPLICATE_NAME_MAX_LENGTH) { "New database user name too long" }
        require(password.length <= DUPLICATE_PASSWORD_MAX_LENGTH) {
            "New database password must be at most $DUPLICATE_PASSWORD_MAX_LENGTH characters"
        }
        val body = JSONObject().apply {
            put("name", name)
            if (user.isNotBlank()) put("user", user)
            if (password.isNotBlank()) put("password", password)
        }.toString()
        return parseOrThrow {
            parseDatabaseDuplication(write("POST", "${databasePath(serverId, databaseId)}/duplicate", token, body))
        }
    }

    private fun parseDatabaseUserEntry(item: JSONObject) = DatabaseUser(
        id = item.getLong("id"),
        user = item.getString("user"),
        remote = item.optBoolean("remote", false),
        remoteIp = item.optString("remote_ip"),
        readonly = item.optBoolean("readonly", false),
        createdAt = item.optString("created_at")
    )

    fun parseDatabaseUsers(json: String): DatabaseUserPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return DatabaseUserPage(
            (0 until data.length()).map { parseDatabaseUserEntry(data.getJSONObject(it)) }, page, lastPage
        )
    }

    fun parseDatabaseUser(json: String): DatabaseUser =
        parseDatabaseUserEntry(JSONObject(json).getJSONObject("data"))

    private fun databaseUsersPath(serverId: Long, databaseId: Long): String =
        "${databasePath(serverId, databaseId)}/users"

    fun databaseUsers(token: String, serverId: Long, databaseId: Long, page: Int = 1, perPage: Int = 15): DatabaseUserPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseDatabaseUsers(
                get("${databaseUsersPath(serverId, databaseId)}?page=$page&per_page=${validatePageSize(perPage)}", token)
            )
        }
    }

    fun databaseUser(token: String, serverId: Long, databaseId: Long, userId: Long): DatabaseUser = parseOrThrow {
        parseDatabaseUser(get("${databaseUsersPath(serverId, databaseId)}/${validateResourceId(userId)}", token))
    }

    fun createDatabaseUser(
        token: String, serverId: Long, databaseId: Long, request: CreateDatabaseUserRequest
    ): DatabaseUser = parseOrThrow {
        parseDatabaseUser(write("POST", databaseUsersPath(serverId, databaseId), token, request.toJson()))
    }

    /** DELETE …/users/{user}: documented response body is empty, so nothing is parsed. */
    fun deleteDatabaseUser(token: String, serverId: Long, databaseId: Long, userId: Long) {
        write("DELETE", "${databaseUsersPath(serverId, databaseId)}/${validateResourceId(userId)}", token, null)
    }

    /** POST …/users/attach: grants an existing server-level user access to this database. */
    fun attachDatabaseUser(token: String, serverId: Long, databaseId: Long, userId: Long): DatabaseUser {
        validateResourceId(userId)
        return parseOrThrow {
            val body = JSONObject().put("user_id", userId).toString()
            parseDatabaseUser(write("POST", "${databaseUsersPath(serverId, databaseId)}/attach", token, body))
        }
    }

    // ---- Database backups domain (database: /backups/database) ----

    private fun databaseBackupPath(backupId: Long): String =
        "/backups/database/${validateResourceId(backupId)}"

    private fun parseDatabaseBackupEntry(item: JSONObject): DatabaseBackup {
        val server = item.optJSONObject("server")
        val database = item.optJSONObject("database")
        val configuration = item.optJSONObject("backup_configuration")
        val excluded = item.optJSONArray("excluded")
        return DatabaseBackup(
            id = item.getLong("id"),
            status = item.optString("status"),
            label = nullableString(item, "label"),
            type = item.optString("type"),
            typeHuman = nullableString(item, "type_human"),
            path = nullableString(item, "path"),
            remotePath = nullableString(item, "remote_path"),
            locations = nullableString(item, "locations"),
            interval = item.optInt("interval", 0),
            tableExclusions = nullableString(item, "table_exclusions"),
            excludedTables = excluded?.let { (0 until it.length()).map(it::getString) } ?: emptyList(),
            keepBackupAmount = item.optInt("keep_backup_amount", 0),
            active = item.optBoolean("active", false),
            compression = nullableString(item, "compression"),
            deleteOnFail = item.optBoolean("delete_on_fail", false),
            customName = nullableString(item, "custom_name"),
            serverId = server?.optLong("id") ?: 0L,
            serverName = server?.optString("name").orEmpty(),
            databaseId = database?.optLong("id") ?: 0L,
            databaseName = database?.optString("name").orEmpty(),
            backupConfigurationId = configuration?.optLong("id") ?: 0L,
            backupConfigurationLabel = configuration?.optString("label").orEmpty(),
            lastBackupAt = nullableString(item, "last_backup_at"),
            nextBackupAt = nullableString(item, "next_backup_at"),
            createdAt = item.optString("created_at")
        )
    }

    fun parseDatabaseBackups(json: String): DatabaseBackupPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return DatabaseBackupPage(
            (0 until data.length()).map { parseDatabaseBackupEntry(data.getJSONObject(it)) }, page, lastPage
        )
    }

    fun parseDatabaseBackup(json: String): DatabaseBackup =
        parseDatabaseBackupEntry(JSONObject(json).getJSONObject("data"))

    /** GET /backups/database: optional documented `server` and `site` filters. */
    fun databaseBackups(
        token: String, serverId: Long? = null, siteId: Long? = null, page: Int = 1, perPage: Int = 15
    ): DatabaseBackupPage {
        require(page >= 1) { "Page must be positive" }
        require(serverId == null || serverId > 0) { "Invalid server ID" }
        require(siteId == null || siteId > 0) { "Invalid site ID" }
        val query = buildString {
            append("?page=$page&per_page=${validatePageSize(perPage)}")
            serverId?.let { append("&server=$it") }
            siteId?.let { append("&site=$it") }
        }
        return parseOrThrow { parseDatabaseBackups(get("/backups/database$query", token)) }
    }

    fun databaseBackup(token: String, backupId: Long): DatabaseBackup = parseOrThrow {
        parseDatabaseBackup(get(databaseBackupPath(backupId), token))
    }

    /** POST /backups/database: the documented response carries only status + message. */
    fun createDatabaseBackup(token: String, request: CreateDatabaseBackupRequest): String = parseOrThrow {
        parseOptionalMessage(write("POST", "/backups/database", token, request.toJson()))
    }

    /** PATCH /backups/database/{id}: the documented response carries the updated backup. */
    fun updateDatabaseBackup(
        token: String, backupId: Long, request: UpdateDatabaseBackupRequest
    ): DatabaseBackup = parseOrThrow {
        parseDatabaseBackup(write("PATCH", databaseBackupPath(backupId), token, request.toJson()))
    }

    /** POST /backups/database/{id}/run: manual trigger, response carries only status + message. */
    fun runDatabaseBackup(token: String, backupId: Long): String = parseOrThrow {
        parseOptionalMessage(
            write("POST", "${databaseBackupPath(backupId)}/run", token, JSONObject().toString())
        )
    }

    /** DELETE /backups/database/{id}: the documented message may be null. */
    fun deleteDatabaseBackup(token: String, backupId: Long): String = parseOrThrow {
        parseOptionalMessage(write("DELETE", databaseBackupPath(backupId), token, null))
    }

    private fun parseBackupChannels(json: String): List<BackupNotificationChannel> {
        val data = JSONObject(json).getJSONArray("data")
        return (0 until data.length()).map { index ->
            data.getJSONObject(index).let { item ->
                BackupNotificationChannel(
                    id = item.getLong("id"),
                    type = item.optString("type"),
                    label = nullableString(item, "label"),
                    location = item.optString("location"),
                    createdAt = item.optString("created_at")
                )
            }
        }
    }

    fun databaseBackupNotificationChannels(token: String, backupId: Long): List<BackupNotificationChannel> =
        parseOrThrow {
            parseBackupChannels(get("${databaseBackupPath(backupId)}/notification-channels", token))
        }

    /** POST …/notification-channels: attaches one channel to one documented location. */
    fun attachDatabaseBackupNotificationChannel(
        token: String, backupId: Long, channelId: Long, location: String
    ): List<BackupNotificationChannel> {
        validateResourceId(channelId)
        validateBackupChannelLocation(location)
        return parseOrThrow {
            val body = JSONObject().put("channel", channelId).put("location", location).toString()
            parseBackupChannels(
                write("POST", "${databaseBackupPath(backupId)}/notification-channels", token, body)
            )
        }
    }

    /** DELETE …/notification-channels/{channel}: without `location`, detaches from every location. */
    fun detachDatabaseBackupNotificationChannel(
        token: String, backupId: Long, channelId: Long, location: String = ""
    ): List<BackupNotificationChannel> {
        require(location.isEmpty() || location in BACKUP_CHANNEL_LOCATIONS) {
            "Unsupported notification location"
        }
        val suffix = if (location.isEmpty()) "" else "?location=$location"
        return parseOrThrow {
            parseBackupChannels(
                write(
                    "DELETE",
                    "${databaseBackupPath(backupId)}/notification-channels/${validateResourceId(channelId)}$suffix",
                    token, null
                )
            )
        }
    }

    // ---- Site file backups domain (site: /backups/file) ----

    private fun fileBackupPath(backupId: Long): String =
        "/backups/file/${validateResourceId(backupId)}"

    /** `active` is documented as boolean in the update shape but as 0/1 in the list shape. */
    private fun flexibleBoolean(item: JSONObject, key: String): Boolean = when (val value = item.opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> false
    }

    private fun parseFileBackupEntry(item: JSONObject): FileBackup {
        val site = item.optJSONObject("site")
        val siteServer = site?.optJSONObject("server")
        val configuration = item.optJSONObject("backup_configuration")
        val excluded = item.optJSONArray("excluded")
        val interval = item.opt("interval")
        val serverIdValue = item.opt("server_id")
        return FileBackup(
            id = item.getLong("id"),
            status = item.optString("status"),
            label = nullableString(item, "label"),
            type = item.optString("type"),
            typeHuman = nullableString(item, "type_human"),
            path = nullableString(item, "path"),
            remotePath = nullableString(item, "remote_path"),
            locations = nullableString(item, "locations"),
            intervalMinutes = (interval as? Number)?.toInt(),
            intervalLabel = (interval as? String).orEmpty(),
            excluded = excluded?.let { (0 until it.length()).map(it::getString) } ?: emptyList(),
            keepBackupAmount = item.optInt("keep_backup_amount", 0),
            active = flexibleBoolean(item, "active"),
            compression = nullableString(item, "compression"),
            deleteOnFail = item.optBoolean("delete_on_fail", false),
            customName = nullableString(item, "custom_name"),
            localPath = nullableString(item, "local_path"),
            siteId = site?.optLong("id") ?: 0L,
            siteDomain = site?.optString("root_domain").orEmpty(),
            serverId = siteServer?.optLong("id") ?: (serverIdValue as? Number)?.toLong() ?: 0L,
            serverName = siteServer?.optString("name").orEmpty(),
            backupConfigurationId = configuration?.optLong("id") ?: 0L,
            backupConfigurationLabel = configuration?.optString("label").orEmpty(),
            lastBackupAt = nullableString(item, "last_backup_at"),
            nextBackupAt = nullableString(item, "next_backup_at"),
            createdAt = item.optString("created_at")
        )
    }

    fun parseFileBackups(json: String): FileBackupPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return FileBackupPage(
            (0 until data.length()).map { parseFileBackupEntry(data.getJSONObject(it)) }, page, lastPage
        )
    }

    fun parseFileBackup(json: String): FileBackup =
        parseFileBackupEntry(JSONObject(json).getJSONObject("data"))

    /** GET /backups/file: optional documented `server` and `site` filters. */
    fun fileBackups(
        token: String, serverId: Long? = null, siteId: Long? = null, page: Int = 1, perPage: Int = 15
    ): FileBackupPage {
        require(page >= 1) { "Page must be positive" }
        require(serverId == null || serverId > 0) { "Invalid server ID" }
        require(siteId == null || siteId > 0) { "Invalid site ID" }
        val query = buildString {
            append("?page=$page&per_page=${validatePageSize(perPage)}")
            serverId?.let { append("&server=$it") }
            siteId?.let { append("&site=$it") }
        }
        return parseOrThrow { parseFileBackups(get("/backups/file$query", token)) }
    }

    fun fileBackup(token: String, backupId: Long): FileBackup = parseOrThrow {
        parseFileBackup(get(fileBackupPath(backupId), token))
    }

    /** POST /backups/file: the documented response carries only status + message. */
    fun createFileBackup(token: String, request: CreateFileBackupRequest): String = parseOrThrow {
        parseOptionalMessage(write("POST", "/backups/file", token, request.toJson()))
    }

    /** PATCH /backups/file/{id}: the documented response carries the updated backup. */
    fun updateFileBackup(
        token: String, backupId: Long, request: UpdateFileBackupRequest
    ): FileBackup = parseOrThrow {
        parseFileBackup(write("PATCH", fileBackupPath(backupId), token, request.toJson()))
    }

    /** POST /backups/file/{id}/run: manual trigger, response carries only status + message. */
    fun runFileBackup(token: String, backupId: Long): String = parseOrThrow {
        parseOptionalMessage(
            write("POST", "${fileBackupPath(backupId)}/run", token, JSONObject().toString())
        )
    }

    /** DELETE /backups/file/{id}: the documented message may be null. */
    fun deleteFileBackup(token: String, backupId: Long): String = parseOrThrow {
        parseOptionalMessage(write("DELETE", fileBackupPath(backupId), token, null))
    }

    fun fileBackupNotificationChannels(token: String, backupId: Long): List<BackupNotificationChannel> =
        parseOrThrow {
            parseBackupChannels(get("${fileBackupPath(backupId)}/notification-channels", token))
        }

    /** POST …/notification-channels: attaches one channel to one documented location. */
    fun attachFileBackupNotificationChannel(
        token: String, backupId: Long, channelId: Long, location: String
    ): List<BackupNotificationChannel> {
        validateResourceId(channelId)
        validateBackupChannelLocation(location)
        return parseOrThrow {
            val body = JSONObject().put("channel", channelId).put("location", location).toString()
            parseBackupChannels(
                write("POST", "${fileBackupPath(backupId)}/notification-channels", token, body)
            )
        }
    }

    /** DELETE …/notification-channels/{channel}: without `location`, detaches from every location. */
    fun detachFileBackupNotificationChannel(
        token: String, backupId: Long, channelId: Long, location: String = ""
    ): List<BackupNotificationChannel> {
        require(location.isEmpty() || location in BACKUP_CHANNEL_LOCATIONS) {
            "Unsupported notification location"
        }
        val suffix = if (location.isEmpty()) "" else "?location=$location"
        return parseOrThrow {
            parseBackupChannels(
                write(
                    "DELETE",
                    "${fileBackupPath(backupId)}/notification-channels/${validateResourceId(channelId)}$suffix",
                    token, null
                )
            )
        }
    }

    fun parseProviders(json: String): ProviderPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return ProviderPage((0 until data.length()).map { parseProviderEntry(data.getJSONObject(it)) }, page, lastPage)
    }

    fun parseProvider(json: String): ProviderCredential = parseProviderEntry(JSONObject(json).getJSONObject("data"))

    private fun parseProviderEntry(item: JSONObject): ProviderCredential {
        val available = item.getJSONObject("provider")
        val plans = available.getJSONArray("plans")
        val regions = available.getJSONArray("regions")
        return ProviderCredential(
            id = item.getLong("id"),
            name = item.getString("name"),
            label = if (item.isNull("label")) "" else item.getString("label"),
            plans = (0 until plans.length()).map { i ->
                plans.getJSONObject(i).let { ProviderOption(it.getString("id"), it.getString("name"), it.optString("description")) }
            },
            regions = (0 until regions.length()).map { i ->
                regions.getJSONObject(i).let { ProviderOption(it.getString("id"), it.getString("name")) }
            }
        )
    }

    fun providers(token: String, page: Int = 1, perPage: Int = 15): ProviderPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow { parseProviders(get("/user/server-providers?page=$page&per_page=${validatePageSize(perPage)}", token)) }
    }

    fun provider(token: String, providerId: Long): ProviderCredential {
        require(providerId > 0) { "Invalid provider ID" }
        return parseOrThrow { parseProvider(get("/user/server-providers/$providerId", token)) }
    }

    fun servers(token: String, page: Int = 1, perPage: Int = 15): ServerPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow { parseServers(get("/servers?page=$page&per_page=${validatePageSize(perPage)}", token)) }
    }

    fun monitoring(token: String, serverId: Long): MonitorSample? {
        require(serverId > 0) { "Invalid server ID" }
        return parseOrThrow { parseMonitoring(get("/servers/$serverId/monitor", token)) }
    }

    // ---- Servers domain: read + write documented routes ----

    /** Numeric-or-string JSON fields (php_version is 7.2 in some responses, "8.4" in others). */
    private fun flexibleString(item: JSONObject, field: String): String =
        item.opt(field)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()

    private fun parseServerDetailEntry(item: JSONObject): ServerDetail {
        val updates = item.optJSONObject("updates")
        val provider = item.optJSONObject("provider")
        val phpVersions = item.optJSONArray("installed_php_versions")
        return ServerDetail(
            id = item.getLong("id"),
            status = item.optString("status"),
            statusId = item.optInt("status_id", -1),
            type = item.optString("type"),
            databaseType = item.optString("database_type"),
            name = item.getString("name"),
            ipAddress = item.optString("ip_address"),
            internalIp = nullableString(item, "internal_ip"),
            sshPort = item.optInt("ssh_port", 0),
            rebootRequired = item.optBoolean("reboot_required", false),
            phpVersion = flexibleString(item, "php_version"),
            phpCliVersion = flexibleString(item, "php_cli_version"),
            mysqlVersion = flexibleString(item, "mysql_version"),
            sitesCount = item.optInt("sites_count", 0),
            monitoring = item.optBoolean("monitoring", false),
            opcache = item.optBoolean("opcache", false),
            installedPhpVersions = phpVersions?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty(),
            updatesPackages = updates?.optInt("packages", 0) ?: 0,
            updatesSecurity = updates?.optInt("security", 0) ?: 0,
            description = nullableString(item, "description"),
            providerName = provider?.optString("name").orEmpty(),
            createdAt = item.optString("created_at"),
            createdHuman = nullableString(item, "created_human"),
            uptimeHuman = nullableString(item, "uptime_human")
        )
    }

    fun parseServerDetail(json: String): ServerDetail =
        parseServerDetailEntry(JSONObject(json).getJSONObject("data"))

    fun server(token: String, serverId: Long): ServerDetail = parseOrThrow {
        parseServerDetail(get("/servers/${validateResourceId(serverId)}", token))
    }

    fun createServer(token: String, request: CreateServerRequest): ServerDetail = parseOrThrow {
        parseServerDetail(write("POST", "/servers", token, request.toJson()))
    }

    fun parseCustomServerCreation(json: String): CustomServerCreation {
        val root = JSONObject(json)
        return CustomServerCreation(
            id = root.getLong("id"),
            name = root.getString("name"),
            publicKey = root.getString("public_key"),
            sshCommand = root.getString("ssh_command"),
            startInstallationUrl = root.getString("start_installation_url"),
            message = root.optString("message")
        )
    }

    fun createCustomServer(token: String, request: CreateCustomServerRequest): CustomServerCreation = parseOrThrow {
        parseCustomServerCreation(write("POST", "/servers/custom", token, request.toJson()))
    }

    fun parseMessage(json: String): String = JSONObject(json).getString("message")

    /** Documented message that may legitimately be null (e.g. delete database backup response). */
    fun parseOptionalMessage(json: String): String {
        val root = JSONObject(json)
        return if (root.isNull("message")) "" else root.optString("message")
    }

    fun startCustomServerInstallation(
        token: String, serverId: Long, installMonitoring: Boolean = false, webhookUrl: String = ""
    ): String {
        if (webhookUrl.isNotBlank()) validateWebhookUrl(webhookUrl)
        val body = JSONObject().apply {
            if (installMonitoring) put("install_monitoring", true)
            if (webhookUrl.isNotBlank()) put("webhook_url", webhookUrl)
        }.toString()
        return parseOrThrow {
            parseMessage(write("POST", "/servers/custom/${validateResourceId(serverId)}/start", token, body))
        }
    }

    fun updateServer(token: String, serverId: Long, name: String, ip: String = "", sshPort: Int? = null): ServerDetail {
        validateServerName(name)
        require(ip.isBlank() || sshPort != null) { "SSH port required when IP is set" }
        if (ip.isNotBlank()) validateIpAddress(ip)
        sshPort?.let { validateSshPort(it) }
        val body = JSONObject().apply {
            put("name", name)
            if (ip.isNotBlank()) {
                put("ip", ip)
                put("ssh_port", sshPort)
            }
        }.toString()
        return parseOrThrow { parseServerDetail(write("PATCH", "/servers/${validateResourceId(serverId)}", token, body)) }
    }

    fun deleteServer(token: String, serverId: Long): ServerDetail = parseOrThrow {
        parseServerDetail(write("DELETE", "/servers/${validateResourceId(serverId)}", token, null))
    }

    fun restartServer(token: String, serverId: Long): String = parseOrThrow {
        parseMessage(write("POST", "/servers/${validateResourceId(serverId)}/restart", token, JSONObject().toString()))
    }

    private fun parseServerLogEntry(item: JSONObject) = ServerLogEntry(
        description = item.getString("description"),
        content = item.optString("content"),
        siteId = if (item.isNull("site_id")) null else item.getLong("site_id"),
        serverId = item.getLong("server_id"),
        createdAt = item.optString("created_at")
    )

    fun parseServerLogs(json: String): ServerLogPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return ServerLogPage((0 until data.length()).map { parseServerLogEntry(data.getJSONObject(it)) }, page, lastPage)
    }

    fun serverLogs(token: String, serverId: Long, page: Int = 1, perPage: Int = 15): ServerLogPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseServerLogs(get("/servers/${validateResourceId(serverId)}/logs?page=$page&per_page=${validatePageSize(perPage)}", token))
        }
    }

    fun parseMonitoredServers(json: String): List<MonitoredServer> {
        val data = JSONObject(json).getJSONArray("data")
        return (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            val statistics = item.optJSONArray("statistics")
            MonitoredServer(
                id = item.getLong("id"),
                name = item.getString("name"),
                ip = item.optString("ip"),
                url = item.optString("url"),
                statistics = statistics?.let { array ->
                    (0 until array.length()).map { parseMonitorSample(array.getJSONObject(it)) }
                }.orEmpty()
            )
        }
    }

    fun monitoredServers(token: String): List<MonitoredServer> = parseOrThrow {
        parseMonitoredServers(get("/servers/monitored", token))
    }

    // ---- Account (user) domain: read-only documented routes ----

    private fun nullableString(item: JSONObject, field: String): String =
        if (item.isNull(field)) "" else item.optString(field)

    fun parseUser(json: String): UserInfo {
        val data = JSONObject(json).getJSONObject("data")
        return UserInfo(
            name = data.getString("name"),
            email = data.getString("email"),
            plan = data.optString("plan"),
            planExpiresAt = nullableString(data, "plan_expires_at"),
            timezone = data.optString("timezone"),
            country = data.optString("country"),
            avatarUrl = data.optString("avatar"),
            createdAt = data.optString("created_at"),
            billingDetails = nullableString(data, "billing_details")
        )
    }

    fun user(token: String): UserInfo = parseOrThrow { parseUser(get("/user", token)) }

    private fun parseBackupConfigurationEntry(item: JSONObject) = BackupConfiguration(
        id = item.getLong("id"),
        label = nullableString(item, "label"),
        type = item.getString("type"),
        humanType = item.optString("humanType"),
        createdAt = item.optString("created_at")
    )

    fun parseBackupConfigurations(json: String): BackupConfigurationPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return BackupConfigurationPage(
            (0 until data.length()).map { parseBackupConfigurationEntry(data.getJSONObject(it)) }, page, lastPage
        )
    }

    fun parseBackupConfiguration(json: String): BackupConfiguration =
        parseBackupConfigurationEntry(JSONObject(json).getJSONObject("data"))

    fun backupConfigurations(token: String, page: Int = 1, perPage: Int = 15): BackupConfigurationPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseBackupConfigurations(get("/user/backup-configurations?page=$page&per_page=${validatePageSize(perPage)}", token))
        }
    }

    fun backupConfiguration(token: String, configurationId: Long): BackupConfiguration = parseOrThrow {
        parseBackupConfiguration(get("/user/backup-configurations/${validateResourceId(configurationId)}", token))
    }

    private fun parseNotificationChannelEntry(item: JSONObject) = NotificationChannel(
        id = item.getLong("id"),
        type = item.getString("type"),
        label = nullableString(item, "label"),
        createdAt = item.optString("created_at")
    )

    fun parseNotificationChannels(json: String): NotificationChannelPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return NotificationChannelPage(
            (0 until data.length()).map { parseNotificationChannelEntry(data.getJSONObject(it)) }, page, lastPage
        )
    }

    fun notificationChannels(token: String, page: Int = 1, perPage: Int = 15): NotificationChannelPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseNotificationChannels(get("/user/notification-channels?page=$page&per_page=${validatePageSize(perPage)}", token))
        }
    }

    private fun parseSourceControlEntry(item: JSONObject) = SourceControlProvider(
        id = item.getLong("id"),
        label = nullableString(item, "label"),
        name = item.optString("name"),
        provider = item.getString("provider"),
        createdAt = item.optString("created_at")
    )

    fun parseSourceControlProviders(json: String): SourceControlPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val (page, lastPage) = pageMeta(root)
        return SourceControlPage(
            (0 until data.length()).map { parseSourceControlEntry(data.getJSONObject(it)) }, page, lastPage
        )
    }

    fun parseSourceControlProvider(json: String): SourceControlProvider =
        parseSourceControlEntry(JSONObject(json).getJSONObject("data"))

    fun sourceControlProviders(token: String, page: Int = 1, perPage: Int = 15): SourceControlPage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow {
            parseSourceControlProviders(get("/user/source-control?page=$page&per_page=${validatePageSize(perPage)}", token))
        }
    }

    fun sourceControlProvider(token: String, providerId: Long): SourceControlProvider = parseOrThrow {
        parseSourceControlProvider(get("/user/source-control/${validateResourceId(providerId)}", token))
    }

    fun parseSourceControlRepositories(json: String): List<SourceControlRepository> {
        val repositories = JSONObject(json).getJSONObject("data").getJSONArray("repositories")
        return (0 until repositories.length()).map { index ->
            repositories.getJSONObject(index).let { item ->
                SourceControlRepository(
                    label = nullableString(item, "label"),
                    name = item.getString("name"),
                    createdAt = item.optString("created_at")
                )
            }
        }
    }

    fun sourceControlRepositories(token: String, providerId: Long): List<SourceControlRepository> = parseOrThrow {
        parseSourceControlRepositories(get("/user/source-control/${validateResourceId(providerId)}/repositories", token))
    }

    /** Wraps JSON decoding failures so 2xx garbage surfaces as a typed error, not a raw crash. */
    private inline fun <T> parseOrThrow(parser: () -> T): T =
        try {
            parser()
        } catch (malformed: org.json.JSONException) {
            throw PloiMalformedPayloadException(malformed)
        }

    internal var httpClient: PloiHttpClient = PloiHttpClient()

    private fun get(path: String, token: String): String = httpClient.get(path, token)

    private fun write(method: String, path: String, token: String, body: String?): String =
        httpClient.request(method, path, token, body)
}
