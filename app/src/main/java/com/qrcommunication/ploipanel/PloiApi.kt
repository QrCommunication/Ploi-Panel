package com.qrcommunication.ploipanel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
internal class PloiHttpException(val status: Int, val retryAfterSeconds: String? = null) : Exception("Ploi HTTP $status")

/** Read-only API foundation. Never persist or log a bearer token. */
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

    fun parseServers(json: String): ServerPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val meta = root.getJSONObject("meta")
        val page = meta.getInt("current_page")
        val lastPage = meta.getInt("last_page")
        require(page >= 1 && lastPage >= 1 && page <= lastPage) { "Invalid pagination metadata" }
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
        val meta = root.getJSONObject("meta")
        val page = meta.getInt("current_page")
        val lastPage = meta.getInt("last_page")
        require(page >= 1 && lastPage >= 1 && page <= lastPage) { "Invalid pagination metadata" }
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
        return parseSites(get("/servers/${validateResourceId(serverId)}/sites?page=$page&per_page=${validatePageSize(perPage)}", token))
    }

    fun site(token: String, serverId: Long, siteId: Long): Site = parseSite(
        get("/servers/${validateResourceId(serverId)}/sites/${validateResourceId(siteId)}", token)
    )

    fun parseProviders(json: String): ProviderPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val meta = root.getJSONObject("meta")
        val page = meta.getInt("current_page")
        val lastPage = meta.getInt("last_page")
        require(page >= 1 && lastPage >= 1 && page <= lastPage) { "Invalid pagination metadata" }
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
        return parseProviders(get("/user/server-providers?page=$page&per_page=${validatePageSize(perPage)}", token))
    }

    fun provider(token: String, providerId: Long): ProviderCredential {
        require(providerId > 0) { "Invalid provider ID" }
        return parseProvider(get("/user/server-providers/$providerId", token))
    }

    fun servers(token: String, page: Int = 1, perPage: Int = 15): ServerPage {
        require(page >= 1) { "Page must be positive" }
        return parseServers(get("/servers?page=$page&per_page=${validatePageSize(perPage)}", token))
    }

    fun monitoring(token: String, serverId: Long): MonitorSample? {
        require(serverId > 0) { "Invalid server ID" }
        return parseMonitoring(get("/servers/$serverId/monitor", token))
    }

    private fun get(path: String, token: String): String {
        val connection = (URL("https://ploi.io/api$path").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer ${validateToken(token)}")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw PloiHttpException(status, connection.getHeaderField("Retry-After"))
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
