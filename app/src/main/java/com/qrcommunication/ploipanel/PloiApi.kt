package com.qrcommunication.ploipanel

import org.json.JSONObject

internal data class Server(val id: Long, val name: String, val status: String, val ipAddress: String)
internal data class ServerPage(val servers: List<Server>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class MonitorSample(val cpu: String, val ram: String, val disk: String, val load: String, val date: String)
internal data class Site(
    val id: Long, val serverId: Long, val domain: String, val status: String,
    val phpVersion: String, val webDirectory: String, val healthUrl: String, val diskUsage: String
)
internal data class SitePage(val sites: List<Site>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
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
        diskUsage = item.optJSONObject("disk_usage")?.optString("human").orEmpty()
    )

    fun sites(token: String, serverId: Long, page: Int = 1, perPage: Int = 15): SitePage {
        require(page >= 1) { "Page must be positive" }
        return parseOrThrow { parseSites(get("/servers/${validateResourceId(serverId)}/sites?page=$page&per_page=${validatePageSize(perPage)}", token)) }
    }

    fun site(token: String, serverId: Long, siteId: Long): Site = parseOrThrow {
        parseSite(get("/servers/${validateResourceId(serverId)}/sites/${validateResourceId(siteId)}", token))
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
