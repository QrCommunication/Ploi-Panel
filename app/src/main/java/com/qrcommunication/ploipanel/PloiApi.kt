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
        val sample = (0 until data.length()).map(data::getJSONObject).maxBy { it.getString("date") }
        return MonitorSample(
            sample.optString("cpu"), sample.optString("ram"), sample.optString("disk"),
            sample.optString("load_average"), sample.getString("date")
        )
    }

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
}
